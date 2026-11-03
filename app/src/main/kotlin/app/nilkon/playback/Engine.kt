package app.nilkon.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.nilkon.data.Song
import app.nilkon.data.Store
import app.nilkon.data.AppLog
import app.nilkon.online.Online
import java.io.File
import java.util.concurrent.Executors

/**
 * The one place that controls playback. The foreground [PlaybackService] owns the ExoPlayer and hands it over here,
 * so music keeps playing with the screen off and the notification / lock screen can control the same player.
 * Everything below runs on the main thread.
 */
object Engine {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(3)
    private var player: Player? = null
    private val pending = ArrayList<(Player) -> Unit>()
    private val registry = HashMap<String, Song>()
    private var app: Context? = null

    // ---- observable state (Compose reads these) -------------------------------------------
    var current by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var waiting by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var messageError = false
    var sleepAtMs by mutableLongStateOf(0L); private set
    var sleepMinutes by mutableIntStateOf(0); private set
    var volume by mutableIntStateOf(75); private set
    var muted by mutableStateOf(false); private set
    val queue: SnapshotStateList<Song> = mutableStateListOf()
    var queueIndex by mutableIntStateOf(0); private set
    val spectrum = FloatArray(32)
    var spectrumTick by mutableIntStateOf(0); private set
    var analysis by mutableStateOf<Analysis?>(null); private set
    var envelope by mutableStateOf<FloatArray?>(null); private set
    var analysisKey by mutableStateOf(""); private set
    var liveSpectrum = false; private set
    var formatInfo by mutableStateOf(""); private set

    private var radioSeed: Song? = null
    private var radioBusy = false
    private var errorRun = 0
    private var retried = false
    private var resumePos = 0L
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    fun init(ctx: Context) { app = ctx.applicationContext }

    fun say(text: String) { if (Store.bool("toasts")) { messageError = false; message = text } }
    fun warn(text: String) { messageError = true; message = text }

    // ---- service hand-over ----------------------------------------------------------------
    fun attach(p: Player) {
        player = p
        p.addListener(listener)
        volume = Store.int("volume").coerceIn(0, 100)
        applyVolume()
        pending.forEach { it(p) }; pending.clear()
        main.post(ticker)
    }

    fun detach() {
        main.removeCallbacks(ticker)
        stopWaiting()
        analysis?.cancelled = true
        player?.removeListener(listener)
        player = null
    }

    fun run(block: (Player) -> Unit) { val p = player; if (p != null) block(p) else pending += block }

    fun register(s: Song) { registry[s.key] = s; Store.remember(s) }
    fun songFor(key: String): Song? = registry[key] ?: Store.onlineMeta[key]

    // ---- building media items -------------------------------------------------------------
    private fun item(s: Song): MediaItem {
        register(s)
        val local = Store.downloads[s.key]
        val uri = if (local != null && File(local).exists()) Uri.fromFile(File(local)).toString() else s.uri
        return MediaItem.Builder().setMediaId(s.key).setUri(uri).setMediaMetadata(
            MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album)
                .setArtworkUri(if (s.art.isNotEmpty()) Uri.parse(s.art) else null).build()
        ).build()
    }

    /** Plays [songs] starting at [start]. A single online song also starts "similar songs" radio. */
    fun play(songs: List<Song>, start: Int = 0, radio: Boolean = false) {
        if (songs.isEmpty()) return
        errorRun = 0
        stopWaiting()
        radioSeed = if (radio && Store.radio) songs[start.coerceIn(songs.indices)].takeIf { it.isOnline } else null
        run { p ->
            p.setMediaItems(songs.map { item(it) }, start.coerceIn(songs.indices), 0L)
            p.prepare(); p.play()
            syncQueue(p)
        }
        if (radioSeed != null) extendRadio()
    }

    fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        run { it.shuffleModeEnabled = true; shuffle = true }
        play(songs, (songs.indices).random())
    }

    fun playNext(s: Song) = run { p -> p.addMediaItem((p.currentMediaItemIndex + 1).coerceAtMost(p.mediaItemCount), item(s)); syncQueue(p) }
    fun addToQueue(s: Song) = run { p -> p.addMediaItem(item(s)); syncQueue(p) }
    fun removeFromQueue(i: Int) = run { p -> if (i in 0 until p.mediaItemCount && i != p.currentMediaItemIndex) { p.removeMediaItem(i); syncQueue(p) } }
    fun jumpTo(i: Int) = run { p -> if (i in 0 until p.mediaItemCount) { p.seekTo(i, 0L); p.play() } }
    fun toggle() = run { p ->
        if (waiting) { resumeNow(p); return@run }
        if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_IDLE) p.prepare(); p.play() }
    }
    fun pause() = run { p -> p.pause() }
    fun next() = run { p -> p.seekToNextMediaItem() }
    fun previous() = run { p -> if (p.currentPosition > 4000) { p.seekTo(0); positionMs = 0 } else p.seekToPreviousMediaItem() }
    fun seek(ms: Long) = run { p -> val t = ms.coerceAtLeast(0); p.seekTo(t); positionMs = t }
    fun seekBy(seconds: Int) = run { p ->
        val dur = if (p.duration > 0) p.duration else Long.MAX_VALUE
        val t = (p.currentPosition + seconds * 1000L).coerceIn(0L, dur)
        p.seekTo(t); positionMs = t
    }
    fun shuffleTo(on: Boolean) = run { p -> p.shuffleModeEnabled = on; shuffle = on }
    fun cycleRepeat() = run { p ->
        p.repeatMode = when (p.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
        repeat = p.repeatMode
    }

    fun isCurrent(key: String) = current?.key == key

    // ---- volume ---------------------------------------------------------------------------
    private fun applyVolume() { Effects.setUserVolume(if (muted) 0f else volume / 100f) }
    fun setVolume(v: Int, save: Boolean = true) {
        volume = v.coerceIn(0, 100)
        if (volume > 0) muted = false
        applyVolume()
        if (save) Store.set("volume", volume)
    }
    fun nudgeVolume(delta: Int) = setVolume(volume + delta)
    fun toggleMute() { muted = !muted; applyVolume() }

    /** Sleep timer: pauses playback after [minutes]; 0 cancels. */
    fun sleepIn(minutes: Int) {
        main.removeCallbacks(sleepRun)
        sleepMinutes = minutes.coerceAtLeast(0)
        if (minutes <= 0) { sleepAtMs = 0; say("Sleep timer off"); return }
        sleepAtMs = System.currentTimeMillis() + minutes * 60_000L
        main.postDelayed(sleepRun, minutes * 60_000L)
        say("Sleep timer: $minutes min")
    }
    private val sleepRun = Runnable { sleepAtMs = 0; sleepMinutes = 0; run { it.pause() }; say("Sleep timer: playback paused") }

    // ---- radio ----------------------------------------------------------------------------
    private fun extendRadio() {
        val seed = radioSeed ?: return
        if (radioBusy) return
        radioBusy = true
        io.execute {
            val found = runCatching { Online.related(seed) }.getOrDefault(emptyList())
            main.post {
                radioBusy = false
                val now = radioSeed
                if (now == null) return@post
                if (now.key != seed.key) { extendRadio(); return@post }
                run { p ->
                    val have = HashSet<String>()
                    for (i in 0 until p.mediaItemCount) have += p.getMediaItemAt(i).mediaId
                    val fresh = found.filter { it.key !in have }
                    if (fresh.isNotEmpty()) { p.addMediaItems(fresh.map { item(it) }); syncQueue(p) }
                    current?.takeIf { it.isOnline }?.let { radioSeed = it }
                }
            }
        }
    }

    private fun warmUp(p: Player) {
        val i = p.nextMediaItemIndex
        if (i < 0) return
        val id = p.getMediaItemAt(i).mediaId
        if (id.startsWith("yt:") && Store.downloads[id] == null) io.execute { runCatching { Online.resolve(id.removePrefix("yt:")) } }
    }

    // ---- network loss: wait and continue the same song ---------------------------------------
    private fun hasInternet(): Boolean {
        val cm = app?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startWaiting(p: Player) {
        resumePos = p.currentPosition
        waiting = true
        warn("Waiting for internet…")
        val cm = app?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        stopCallback()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { if (waiting) Engine.run { resumeNow(it) } } }
        }
        netCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        main.postDelayed(netPoll, 5000)
    }

    private val netPoll = object : Runnable {
        override fun run() {
            if (!waiting) return
            if (hasInternet()) { Engine.run { p -> resumeNow(p) } } else main.postDelayed(this, 5000)
        }
    }

    private fun stopCallback() {
        val cm = app?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        netCallback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
        netCallback = null
    }

    private fun stopWaiting() { waiting = false; stopCallback(); main.removeCallbacks(netPoll) }

    private fun resumeNow(p: Player) {
        stopWaiting()
        errorRun = 0; retried = false
        for (k in 0 until p.mediaItemCount) { val id = p.getMediaItemAt(k).mediaId; if (id.startsWith("yt:")) Online.forget(id.removePrefix("yt:")) }
        p.prepare(); p.seekTo(resumePos); p.play()
        say("Back online - continuing")
    }

    // ---- analysis (real spectrum / seek-bar waveform) --------------------------------------------
    private fun startAnalysis(s: Song?) {
        analysis?.cancelled = true
        analysis = null; envelope = null; analysisKey = s?.key ?: ""
        Effects.normalizeDb = 0f
        liveSpectrum = true
        formatInfo = ""
        if (s == null) return
        val ctx = app ?: return
        val dl = Store.downloads[s.key]
        val source = when {
            dl != null && File(dl).exists() -> dl
            !s.isOnline -> s.uri
            else -> null
        } ?: return
        if (source.isEmpty()) return
        val an = Analyzer.start(ctx, source)
        analysis = an
        liveSpectrum = false
        formatInfo = if (s.isOnline) "Downloaded" else "On this phone"
    }

    private fun pollAnalysis() {
        val an = analysis ?: return
        if (an.done && envelope == null && analysisKey == current?.key) {
            envelope = an.envelope
            if (an.failed) { analysis = null; liveSpectrum = true }
            else if (!an.loudnessDb.isNaN()) Effects.normalizeDb = (-17f - an.loudnessDb).coerceIn(-9f, 6f)
        }
    }

    // ---- state mirror ---------------------------------------------------------------------
    private fun syncQueue(p: Player) {
        val list = ArrayList<Song>(p.mediaItemCount)
        for (i in 0 until p.mediaItemCount) {
            val mi = p.getMediaItemAt(i)
            list += songFor(mi.mediaId) ?: Song(mi.mediaId, mi.mediaMetadata.title?.toString() ?: "Song",
                mi.mediaMetadata.artist?.toString() ?: "")
        }
        queue.clear(); queue.addAll(list)
        queueIndex = p.currentMediaItemIndex
    }

    private fun refreshCurrent(p: Player) {
        val id = p.currentMediaItem?.mediaId
        current = id?.let { k -> songFor(k) ?: queue.firstOrNull { it.key == k } }
        queueIndex = p.currentMediaItemIndex
        durationMs = if (p.duration > 0) p.duration else current?.durationMs ?: 0
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = player ?: return
            retried = false
            refreshCurrent(p)
            positionMs = 0
            current?.let { Store.recordPlay(it); Store.lastKey = it.key }
            Effects.applyFor(current?.key)
            startAnalysis(current)
            if (radioSeed != null && p.mediaItemCount - p.currentMediaItemIndex <= 3) extendRadio()
            warmUp(p)
        }
        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing; if (playing) { errorRun = 0; if (waiting) stopWaiting() } }
        override fun onPlaybackStateChanged(state: Int) {
            val p = player ?: return
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) { durationMs = p.duration.coerceAtLeast(0); errorRun = 0 }
        }
        override fun onTimelineChanged(t: androidx.media3.common.Timeline, reason: Int) { player?.let { syncQueue(it); refreshCurrent(it) } }
        override fun onShuffleModeEnabledChanged(on: Boolean) { shuffle = on }
        override fun onRepeatModeChanged(m: Int) { repeat = m }
        override fun onAudioSessionIdChanged(id: Int) { Effects.rebuild(id) }
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            positionMs = new.positionMs
        }
        override fun onPlayerError(error: PlaybackException) {
            AppLog.add("player", "${error.errorCodeName} (${error.errorCode}) song=${player?.currentMediaItem?.mediaId ?: "?"}", error)
            var c: Throwable? = error
            while (c != null) {
                if (c is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                    AppLog.add("player", "server said ${c.responseCode} for " + Online.describe(c.dataSpec.uri.toString()) + " headers=" + c.headerFields.keys.take(8))
                c = c.cause
            }
            val p = player ?: return
            val key = p.currentMediaItem?.mediaId ?: return
            val online = key.startsWith("yt:") && (Store.downloads[key]?.let { File(it).exists() } != true)
            if (online) {
                Online.forget(key.removePrefix("yt:"))
                if (!hasInternet()) { startWaiting(p); return }
            }
            if (!retried) {
                retried = true
                val pos = p.currentPosition
                p.prepare(); p.seekTo(pos); p.play(); return
            }
            retried = false
            if (online && !hasInternet()) { startWaiting(p); return }
            errorRun++
            warn("Could not play this song - " + Online.friendlyError(error.cause ?: error))
            if (errorRun >= 3) { p.pause(); warn("Playback stopped after several errors. Check your connection."); return }
            if (p.hasNextMediaItem()) { p.seekToNextMediaItem(); p.prepare(); p.play() } else p.pause()
        }
    }

    private val ticker = object : Runnable {
        var n = 0
        override fun run() {
            val p = player ?: return
            if (p.isPlaying) {
                positionMs = p.currentPosition
                if (p.duration > 0) durationMs = p.duration
                if (++n % 20 == 0) { Store.lastPos = positionMs; Store.touch() }
            }
            if (n % 10 == 0) pollAnalysis()
            n++
            if (Effects.pollSpectrum(spectrum) == true) spectrumTick++
            main.postDelayed(this, if (p.isPlaying) 33 else 300)
        }
    }

    /** Called once at start: shows the last song paused so Play continues from where you stopped. */
    fun restoreLast(all: List<Song>) {
        if (!Store.bool("resume_last")) return
        val key = Store.lastKey
        if (key.isEmpty() || current != null) return
        val s = all.firstOrNull { it.key == key } ?: songFor(key) ?: return
        run { p ->
            if (p.mediaItemCount == 0) {
                p.setMediaItems(listOf(item(s)), 0, Store.lastPos); p.prepare(); p.pause()
                syncQueue(p); refreshCurrent(p); positionMs = Store.lastPos
                startAnalysis(current)
            }
        }
    }

    @Suppress("unused")
    private fun unusedF() = mutableFloatStateOf(0f)
}
