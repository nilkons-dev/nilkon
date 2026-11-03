package app.nilkon

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nilkon.data.LocalLibrary
import app.nilkon.data.Song
import app.nilkon.data.AppLog
import app.nilkon.data.Store
import app.nilkon.online.Online
import app.nilkon.playback.Effects
import app.nilkon.playback.Engine
import app.nilkon.ui.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.random.Random

const val ALL = "__all"
const val LIKED = "__liked"
const val RECENT = "__recent"
const val PL_PREFIX = "pl:"

class DlState(val state: String, val pct: Float = 0f, val error: String = "")

/** What the equalizer screen edits: the all-songs EQ, or the EQ of one song. */
class EqRequest(val songKey: String?, val songTitle: String, val songScope: Boolean)

/**
 * Everything the screens show and every action they trigger: library, collections, online search, downloads,
 * playlists, menus and dialogs. Store data is plain collections, so [rev] is bumped after every change and the
 * screens read it to refresh.
 */
class AppModel(val app: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(2)

    /** Bumped whenever playlists, favourites, history, downloads or the library change. */
    var rev by mutableIntStateOf(0)
        private set
    fun changed() { rev++; Store.touch() }

    // ---- navigation ------------------------------------------------------------------------
    var page by mutableStateOf("home")            // home | online | collection | now | settings
    var lastPage = "home"
    var collection by mutableStateOf(ALL)
    var collectionQuery by mutableStateOf("")
    var settingsPage by mutableStateOf("")        // "" = list
    var eqRequest by mutableStateOf<EqRequest?>(null)

    fun go(p: String) {
        if (p == "now" && page != "now") lastPage = page
        page = p
        if (p != "settings") settingsPage = ""
    }
    fun showCollection(name: String) { collection = name; collectionQuery = ""; page = "collection" }

    // ---- dialogs ---------------------------------------------------------------------------
    var menu by mutableStateOf<MenuSpec?>(null)
    var prompt by mutableStateOf<PromptSpec?>(null)
    var confirm by mutableStateOf<ConfirmSpec?>(null)
    var notice by mutableStateOf<NoticeSpec?>(null)
    // pickers are launched by the activity; the screens just call these
    var pickFiles: () -> Unit = {}
    var pickFolder: () -> Unit = {}
    var pickImage: () -> Unit = {}
    var openAppSettings: () -> Unit = {}
    var requestAudioAccess: () -> Unit = {}
    var requestMic: () -> Unit = {}
    var hasMic by mutableStateOf(false)

    fun anyDialog() = menu != null || prompt != null || confirm != null || notice != null

    // ---- library ---------------------------------------------------------------------------
    var hasAudioPermission by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    var library by mutableStateOf<List<Song>>(emptyList())
        private set
    private var local = emptyList<Song>()
    private val byKeyMap = HashMap<String, Song>()

    fun scan() {
        if (scanning) return
        scanning = true
        io.execute {
            val l = if (hasAudioPermission) runCatching { LocalLibrary.scan(app) }.getOrDefault(emptyList()) else emptyList()
            main.post { local = l; scanning = false; rebuild(); Engine.restoreLast(library) }
        }
    }

    fun rebuild() {
        val out = ArrayList<Song>()
        val seen = HashSet<String>()
        fun add(s: Song) {
            if (s.key in Store.hidden || !seen.add(s.key)) return
            out += s.copy(title = Store.titles[s.key] ?: s.title)
        }
        local.forEach { add(it) }
        Store.added.values.forEach { add(it) }
        Store.downloads.keys.forEach { k -> Store.onlineMeta[k]?.let { add(it) } }
        library = out
        byKeyMap.clear()
        out.forEach { byKeyMap[it.key] = it; Engine.register(it) }
        rev++
    }

    fun songByKey(key: String): Song? = byKeyMap[key] ?: Engine.songFor(key)

    fun songsOf(name: String): List<Song> = when {
        name == ALL -> library
        name == LIKED -> Store.liked.toList().asReversed().mapNotNull { songByKey(it) }
        name == RECENT -> Store.history.toList().asReversed().mapNotNull { songByKey(it) }
        name.startsWith(PL_PREFIX) -> Store.playlists[name.removePrefix(PL_PREFIX)]?.toList()?.mapNotNull { songByKey(it) } ?: emptyList()
        else -> emptyList()
    }

    /** Can the songs of the open collection be re-ordered by hand? */
    fun canReorder(): Boolean = collectionQuery.isBlank() && (collection == LIKED || collection.startsWith(PL_PREFIX))

    /** Moves [key] one place down (+1) or up (-1) in the open collection. */
    fun moveStep(key: String, dir: Int): Boolean {
        val c = collection
        val ok = if (c == LIKED) Store.moveLiked(key, dir)
        else if (c.startsWith(PL_PREFIX)) {
            val n = c.removePrefix(PL_PREFIX)
            val i = Store.playlists[n]?.indexOf(key) ?: -1
            Store.movePlaylistItem(n, i, i + dir)
        } else false
        if (ok) changed()
        return ok
    }

    fun visibleSongs(): List<Song> {
        val all = songsOf(collection)
        val q = collectionQuery.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { (it.title + " " + it.artist + " " + it.album).lowercase().contains(q) }
    }

    fun collectionTitle(name: String): String = when {
        name == ALL -> "All tracks"
        name == LIKED -> "Liked songs"
        name == RECENT -> "Recently played"
        else -> name.removePrefix(PL_PREFIX)
    }

    // ---- suggestions (weighted towards songs played most, same rule as the desktop player) --------------
    var suggestions by mutableStateOf<List<Song>>(emptyList())
    fun makeSuggestions(n: Int, exclude: Set<String> = emptySet()): List<Song> {
        val pool = library.filter { it.key !in exclude }
        if (pool.isEmpty()) return emptyList()
        return pool.map { s ->
            val c = Store.plays[s.key] ?: 0
            val w = if (c > 0) (c + 0.5).pow(1.6) else 0.35
            Random.nextDouble().pow(1.0 / w) to s
        }.sortedByDescending { it.first }.take(n).map { it.second }
    }
    fun refreshSuggestions(avoidCurrent: Boolean = true) {
        suggestions = makeSuggestions(12, if (avoidCurrent) setOfNotNull(Engine.current?.key) else emptySet())
    }
    fun validSuggestions(): List<Song> {
        val ok = suggestions.filter { byKeyMap.containsKey(it.key) }
        if (ok.isEmpty() && library.isNotEmpty()) { val n = makeSuggestions(12); suggestions = n; return n }
        return ok
    }

    // ---- playing ---------------------------------------------------------------------------
    /** Play/pause [s] inside [list] (the list becomes the queue). */
    fun playFrom(list: List<Song>, s: Song) {
        if (Engine.isCurrent(s.key)) { Engine.toggle(); return }
        val i = list.indexOfFirst { it.key == s.key }
        if (i < 0) { Engine.play(listOf(s), 0, s.isOnline); return }
        Engine.play(list, i, radio = s.isOnline && list.size == 1)
    }
    fun playOnline(s: Song) {
        Store.remember(s)
        if (Engine.isCurrent(s.key)) { Engine.toggle(); return }
        Engine.play(listOf(s), 0, radio = true)
    }
    fun playMix() {
        if (library.isEmpty()) { Engine.say("Add some music to get started."); return }
        val l = validSuggestions()
        if (l.isNotEmpty()) Engine.play(l, 0)
    }
    fun shuffleAll() {
        if (library.isEmpty()) { Engine.say("Add some music to get started."); return }
        Engine.playShuffled(library)
    }
    fun playCollection(shuffle: Boolean) {
        val l = visibleSongs()
        if (l.isEmpty()) return
        if (shuffle) Engine.playShuffled(l) else Engine.play(l, 0)
    }

    fun toggleLike(s: Song) {
        val now = Store.toggleLike(s)
        rev++
        Engine.say(if (now) "Added to Liked songs" else "Removed from Liked songs")
    }

    // ---- playlists -------------------------------------------------------------------------
    fun createPlaylist(add: Song? = null) {
        prompt = PromptSpec("New playlist", "Give your playlist a name. You can add songs from the ⋯ menu.",
            "e.g. Late night drive", "", "Create", "list-plus") { raw ->
            val name = raw.trim().take(60)
            if (!Store.createPlaylist(name)) {
                notice = NoticeSpec("Can't create playlist", "A playlist with that name already exists, or the name is empty.")
            } else {
                if (add != null) Store.addToPlaylist(name, add)
                changed()
                Engine.say("Playlist “$name” created" + if (add != null) " and song added" else "")
            }
        }
    }
    fun addToPlaylist(name: String, s: Song) {
        if (Store.addToPlaylist(name, s)) Engine.say("Added to $name") else Engine.say("Already in $name")
        changed()
    }
    fun renamePlaylist(name: String) {
        prompt = PromptSpec("Rename playlist", "", "Playlist name", name, "Save", "edit") { raw ->
            if (Store.renamePlaylist(name, raw)) {
                if (collection == PL_PREFIX + name) collection = PL_PREFIX + raw.trim().take(60)
                changed()
            } else notice = NoticeSpec("Can't rename", "That name is already used by another playlist.")
        }
    }
    fun deletePlaylist(name: String) {
        confirm = ConfirmSpec("Delete playlist", "“$name” will be deleted. The songs in it stay in your library.", "Delete", true, "trash") {
            Store.deletePlaylist(name)
            if (collection == PL_PREFIX + name) collection = ALL
            changed()
            Engine.say("Playlist deleted")
        }
    }
    fun playlistPicker(s: Song) {
        val items = ArrayList<MenuItem>()
        for (n in Store.playlists.keys.toList()) {
            val has = Store.playlists[n]?.contains(s.key) == true
            items += MenuItem("list-music", n, enabled = !has, checked = has) { addToPlaylist(n, s) }
        }
        if (items.isNotEmpty()) items += MenuItem.divider()
        items += MenuItem("plus", "New playlist…") { createPlaylist(s) }
        menu = MenuSpec("Add to playlist", s.title, items)
    }
    fun libraryMenu() {
        val items = ArrayList<MenuItem>()
        items += MenuItem("plus", "Add music files") { pickFiles() }
        items += MenuItem("folder-plus", "Add a music folder") { pickFolder() }
        items += MenuItem("reset", "Rescan this phone") { scan() }
        if (collection.startsWith(PL_PREFIX)) {
            val n = collection.removePrefix(PL_PREFIX)
            items += MenuItem.divider()
            items += MenuItem("edit", "Rename playlist…") { renamePlaylist(n) }
            items += MenuItem("trash", "Delete playlist…", danger = true) { deletePlaylist(n) }
        }
        menu = MenuSpec(collectionTitle(collection), "", items)
    }

    fun playlistMenu(name: String) {
        menu = MenuSpec(name, "${Store.playlists[name]?.size ?: 0} songs", listOf(
            MenuItem("edit", "Rename playlist…") { renamePlaylist(name) },
            MenuItem("trash", "Delete playlist…", danger = true) { deletePlaylist(name) },
        ))
    }

    // ---- song menu -------------------------------------------------------------------------
    fun isDownloaded(key: String) = Store.downloads[key]?.let { File(it).exists() } == true

    fun songMenu(s: Song) {
        val playingNow = Engine.isCurrent(s.key) && Engine.isPlaying
        val liked = s.key in Store.liked
        val onlineOnly = s.isOnline && !isDownloaded(s.key)
        val inPlaylist = if (collection.startsWith(PL_PREFIX)) collection.removePrefix(PL_PREFIX) else null
        val list = ArrayList<MenuItem>()
        list += MenuItem(if (playingNow) "pause" else "play", if (playingNow) "Pause" else "Play") {
            if (page == "online") playOnline(s) else playFrom(visibleSongsFor(s), s)
        }
        list += MenuItem(if (liked) "heart-fill" else "heart", if (liked) "Remove from favorites" else "Add to favorites") { toggleLike(s) }
        if (!onlineOnly) list += MenuItem("edit", "Rename song…") { renameSong(s) }
        list += MenuItem.divider()
        list += MenuItem("list-plus", "Add to playlist") { playlistPicker(s) }
        if (inPlaylist != null && Store.playlists[inPlaylist]?.contains(s.key) == true) {
            list += MenuItem("x", "Remove from “$inPlaylist”") { Store.removeFromPlaylist(inPlaylist, s.key); changed() }
        }
        list += MenuItem("skip-song", "Play next") { Store.remember(s); Engine.playNext(s); Engine.say("Plays next") }
        list += MenuItem("list-music", "Add to queue") { Store.remember(s); Engine.addToQueue(s); Engine.say("Added to queue") }
        list += MenuItem("eq", "Customize equalizer for this song…") { openEq(s, true) }
        list += MenuItem.divider()
        if (s.isOnline) {
            val st = dl[s.key]?.state
            val label = when { isDownloaded(s.key) -> "Downloaded"; st == "queued" || st == "downloading" -> "Cancel download"; else -> "Download" }
            list += MenuItem("download", label, enabled = !isDownloaded(s.key)) { requestDownload(s) }
        }
        if (!onlineOnly) {
            list += MenuItem("x", "Remove from library") { removeSong(s, false) }
            list += MenuItem("trash", "Delete file…", danger = true) { removeSong(s, true) }
        }
        menu = MenuSpec(s.title, s.artist, list)
    }

    private fun visibleSongsFor(s: Song): List<Song> {
        val l = if (page == "collection") visibleSongs() else emptyList()
        return if (l.any { it.key == s.key }) l else listOf(s)
    }

    fun renameSong(s: Song) {
        prompt = PromptSpec("Rename song", "Only the name shown in NILKON changes, not the file. Leave it empty to restore the original name.",
            "Song name", s.title, "Save", "edit", allowEmpty = true) { t ->
            if (t.isEmpty()) Store.titles.remove(s.key) else Store.titles[s.key] = t.take(200)
            Store.touch()
            val orig = (local.firstOrNull { it.key == s.key } ?: Store.added[s.key] ?: Store.onlineMeta[s.key])
            rebuild()
            if (orig != null && t.isEmpty()) Engine.register(orig)
        }
    }

    // ---- removing songs --------------------------------------------------------------------
    fun removeSong(s: Song, deleteFile: Boolean) {
        if (!deleteFile) {
            confirm = ConfirmSpec("Remove from library", "“${s.title}” disappears from NILKON. The file itself stays on your phone.", "Remove", false, "x") {
                forget(s)
                Engine.say("Removed from library")
            }
        } else {
            confirm = ConfirmSpec("Delete file", "“${s.title}” will be permanently deleted from your phone. This cannot be undone.", "Delete", true, "trash") {
                deleteFileOf(s)
            }
        }
    }

    private fun forget(s: Song) {
        if (s.key.startsWith("local:")) Store.hidden += s.key
        Store.added.remove(s.key)
        Store.downloads.remove(s.key)
        Store.liked.remove(s.key); Store.history.remove(s.key); Store.plays.remove(s.key)
        Store.playlists.values.forEach { it.remove(s.key) }
        Store.touch()
        rebuild()
    }

    /** Called by the activity when it can show the system "allow delete" screen. */
    var launchDelete: ((IntentSender) -> Unit)? = null
    private var pendingDelete: Song? = null

    fun deleteFinished(ok: Boolean) {
        val s = pendingDelete ?: return
        pendingDelete = null
        if (ok) { forget(s); Engine.say("File deleted") } else Engine.say("Nothing was deleted")
    }

    private fun deleteFileOf(s: Song) {
        try {
            when {
                s.isOnline -> { Store.downloads[s.key]?.let { File(it).delete() }; forget(s); Engine.say("File deleted") }
                s.uri.startsWith("content://media") -> {
                    val uri = Uri.parse(s.uri)
                    try {
                        if (app.contentResolver.delete(uri, null, null) > 0) { forget(s); Engine.say("File deleted") }
                        else Engine.warn("The file could not be deleted")
                    } catch (e: SecurityException) {
                        val sender: IntentSender? = if (Build.VERSION.SDK_INT >= 30)
                            MediaStore.createDeleteRequest(app.contentResolver, listOf(uri)).intentSender
                        else if (Build.VERSION.SDK_INT >= 29 && e is android.app.RecoverableSecurityException)
                            e.userAction.actionIntent.intentSender
                        else null
                        val l = launchDelete
                        if (sender != null && l != null) { pendingDelete = s; l(sender) } else Engine.warn("Android did not allow deleting this file")
                    }
                }
                else -> {
                    val ok = runCatching { DocumentsContract.deleteDocument(app.contentResolver, Uri.parse(s.uri)) }.getOrDefault(false)
                    if (ok) { forget(s); Engine.say("File deleted") } else Engine.warn("The file could not be deleted")
                }
            }
        } catch (e: Exception) {
            Engine.warn("The file could not be deleted")
        }
    }

    // ---- adding files and folders ----------------------------------------------------------------
    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        io.execute {
            var n = 0
            for (u in uris) {
                runCatching { app.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val s = readSong(u) ?: continue
                if (Store.added.containsKey(s.key) || byKeyMap.containsKey(s.key)) continue
                Store.added[s.key] = s; Store.hidden.remove(s.key); n++
            }
            main.post {
                if (n > 0) { Store.touch(); rebuild() }
                Engine.say(if (n > 0) "Added $n song${if (n != 1) "s" else ""}" else "No new songs were added")
            }
        }
    }

    fun addFolder(tree: Uri) {
        runCatching { app.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        io.execute {
            val found = ArrayList<Uri>()
            runCatching { walk(tree, DocumentsContract.getTreeDocumentId(tree), found, 0) }
            main.post { if (found.isEmpty()) Engine.say("No music found in that folder") else addFiles(found) }
        }
    }

    private fun walk(tree: Uri, docId: String, out: MutableList<Uri>, depth: Int) {
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        app.contentResolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0); val mime = c.getString(1) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { if (depth < 8) walk(tree, id, out, depth + 1) }
                else if (mime.startsWith("audio/")) out += DocumentsContract.buildDocumentUriUsingTree(tree, id)
            }
        }
    }

    private fun readSong(u: Uri): Song? {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(app, u)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val name = app.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }?.substringBeforeLast('.')
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: name ?: "Unknown"
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() } ?: "Unknown artist"
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val key = "uri:" + MessageDigest.getInstance("SHA-1").digest(u.toString().toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
            return Song(key, title, artist, album, dur, u.toString(), "")
        } catch (_: Exception) {
            return null
        } finally { runCatching { mmr.release() } }
    }

    // ---- online search ---------------------------------------------------------------------------
    var onlineQuery by mutableStateOf("")
        private set
    var onlineResults by mutableStateOf<List<Song>>(emptyList())
    var onlineState by mutableStateOf("idle")      // idle | loading | error | empty | done
    var onlineError by mutableStateOf("")
    private var seq = 0
    private val searchRun = Runnable { runSearch() }

    fun typeOnlineQuery(q: String) {
        onlineQuery = q
        main.removeCallbacks(searchRun)
        if (q.trim().length < 2) { seq++; onlineState = "idle"; onlineResults = emptyList(); return }
        onlineState = "loading"
        main.postDelayed(searchRun, 450)
    }

    fun searchNow() { main.removeCallbacks(searchRun); if (onlineQuery.trim().length >= 2) runSearch() }

    private fun runSearch() {
        val q = onlineQuery.trim()
        if (q.length < 2) return
        val my = ++seq
        onlineState = "loading"
        io.execute {
            val res = runCatching { Online.search(q) }
            main.post {
                if (my != seq) return@post
                res.onSuccess { l ->
                    l.forEach { Engine.register(it) }
                    onlineResults = l; onlineState = if (l.isEmpty()) "empty" else "done"
                }.onFailure { e -> onlineResults = emptyList(); onlineState = "error"; onlineError = Online.friendlyError(e); AppLog.add("search", "failed for \"" + onlineQuery.take(60) + "\"", e) }
            }
        }
    }

    // ---- downloads -------------------------------------------------------------------------------
    val dl = mutableStateMapOf<String, DlState>()
    private val dlPool = Executors.newSingleThreadExecutor()
    private val dlCancel: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun downloadDir(): File {
        val base = app.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(app.filesDir, "music")
        return File(base, "NILKON")
    }

    fun requestDownload(s: Song) {
        val st = dl[s.key]?.state
        if (st == "queued" || st == "downloading") {
            dlCancel += s.key
            if (st == "queued") dl.remove(s.key)
            Engine.say("Download cancelled")
            return
        }
        if (isDownloaded(s.key)) { Engine.say("Already in your library."); return }
        Store.remember(s)
        dlCancel -= s.key
        dl[s.key] = DlState("queued")
        dlPool.execute {
            if (s.key in dlCancel) { dlCancel -= s.key; return@execute }
            main.post { dl[s.key] = DlState("downloading", 0f) }
            try {
                val f = Online.download(s, downloadDir(), { p -> main.post { if (dl[s.key]?.state == "downloading") dl[s.key] = DlState("downloading", p) } }, { s.key in dlCancel })
                main.post {
                    dl.remove(s.key)
                    Store.downloads[s.key] = f.path
                    Store.remember(s)
                    rebuild(); Store.touch()
                    Engine.say("Downloaded “${s.title}”")
                }
            } catch (e: Throwable) {
                main.post {
                    if (s.key in dlCancel) dl.remove(s.key) else { AppLog.add("download", "failed ${s.key}", e); dl[s.key] = DlState("error", 0f, Online.friendlyError(e)); Engine.warn("Download failed: " + Online.friendlyError(e)) }
                    dlCancel -= s.key
                }
            }
        }
    }

    // ---- equalizer -------------------------------------------------------------------------------
    fun openEq(song: Song? = Engine.current, songScope: Boolean = false) {
        eqRequest = EqRequest(song?.key, song?.title ?: "", songScope && song != null)
    }
    fun applyEq() {
        Effects.applyFor(Engine.current?.key)
        refreshVisualEq()
    }
    fun refreshVisualEq() {
        val e = Store.eqFor(Engine.current?.key ?: "")
        V.setEq(e.values, e.preamp, e.enabled)
    }

    // ---- appearance and data ----------------------------------------------------------------------
    fun pickCustomBackground(u: Uri): Boolean {
        return try {
            val dir = File(app.filesDir, "backgrounds"); dir.mkdirs()
            dir.listFiles()?.filter { it.name.startsWith("custom_") }?.forEach { it.delete() }
            val f = File(dir, "custom_${System.currentTimeMillis()}.img")
            app.contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: return false
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth <= 0) { f.delete(); notice = NoticeSpec("Background", "This image could not be opened."); return false }
            Store.set("custom_bg", f.path); Store.set("background", "custom")
            true
        } catch (e: Exception) { notice = NoticeSpec("Background", "This image could not be opened."); false }
    }

    fun clearRecent() { Store.history.clear(); changed(); Engine.say("Recently played cleared") }
    fun resetStats() { Store.plays.clear(); suggestions = emptyList(); changed(); Engine.say("Listening statistics reset") }

    /** Settings > Problem log > "Test online playback": tries to open a stream and writes every step to the log. */
    fun testOnline() {
        AppLog.add("test", "online playback test started")
        io.execute {
            Online.forget("dQw4w9WgXcQ")
            try {
                val u = Online.resolve("dQw4w9WgXcQ")
                AppLog.add("test", "OK - " + Online.describe(u))
            } catch (e: Throwable) { AppLog.add("test", "FAILED", e) }
            main.post { Engine.say("Test finished - see the log") }
        }
    }

    fun updateEngine() {
        Online.clearCache()
        Engine.say("Music engine refreshed")
    }

    fun sleepLabel(): String {
        if (Engine.sleepAtMs <= 0L) return ""
        val left = ((Engine.sleepAtMs - System.currentTimeMillis()) / 60000L).toInt() + 1
        return "Sleep in ${left.coerceAtLeast(1)} min"
    }

    fun libraryStats(): String = "${library.size} songs · ${Store.liked.size} liked · ${Store.playlists.size} playlist${if (Store.playlists.size != 1) "s" else ""}"
}
