package app.nilkon.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.nilkon.MainActivity
import app.nilkon.data.Store
import app.nilkon.online.Online
import java.io.IOException

/**
 * Foreground media service. It owns the player and the media session, so music keeps playing when the app is
 * minimised or the screen is off, and the notification / lock screen / headset buttons can control it.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var exo: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Online.init(this)

        val http = OkHttpDataSource.Factory(Online.client)
        val upstream = DefaultDataSource.Factory(this, http)
        // "nilkon://yt/<id>" is turned into the real stream right before it is loaded (see YtSource)
        val resolving = DataSource.Factory { YtSource(upstream.createDataSource()) }
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 60_000, 1_500, 3_000).build()
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setLoadControl(load)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)   // pauses for calls, ducks for alerts
            .setHandleAudioBecomingNoisy(true)                                // pauses when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)                                  // keeps CPU + Wi-Fi awake with the screen off
            .build()
        exo = p
        Effects.volumeSetter = { v -> p.volume = v }
        Effects.rebuild(p.audioSessionId)

        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, p).setSessionActivity(open).build()
        Engine.attach(p)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away keeps playing; only stop when nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = exo
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        Store.save()
        Engine.detach()
        Effects.release()
        session?.release(); session = null
        exo?.release(); exo = null
        super.onDestroy()
    }
}
