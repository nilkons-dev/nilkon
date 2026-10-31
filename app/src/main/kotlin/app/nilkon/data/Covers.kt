package app.nilkon.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.nilkon.online.Online
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Cover pictures: embedded art of phone songs, thumbnails of online songs (cached on disk). */
object Covers {
    private var ctx: Context? = null
    private val main = Handler(Looper.getMainLooper())
    private val cache = LruCache<String, ImageBitmap>(90)
    private val failed = HashSet<String>()
    private val loading = HashSet<String>()
    private val pool = Executors.newFixedThreadPool(3)

    /** Changes whenever a new picture is ready, so Compose code that called [peek] redraws. */
    var version by mutableIntStateOf(0)
        private set

    fun init(c: Context) { ctx = c.applicationContext }

    private fun bucket(px: Int): Int = when { px <= 96 -> 96; px <= 200 -> 200; px <= 400 -> 400; else -> 640 }

    /** The picture of [song] (null while it loads or when there is none). */
    fun peek(song: Song?, px: Int): ImageBitmap? {
        version
        if (song == null) return null
        val b = bucket(px)
        val key = song.key + "@" + b
        cache.get(key)?.let { return it }
        // a bigger one that is already loaded is fine for a small spot
        for (alt in intArrayOf(640, 400, 200)) if (alt > b) cache.get(song.key + "@" + alt)?.let { return it }
        if (key in failed || key in loading) return null
        loading += key
        val c = ctx ?: return null
        pool.execute {
            val bmp = runCatching { load(c, song, b) }.getOrNull()
            main.post {
                loading -= key
                if (bmp != null) { cache.put(key, bmp.asImageBitmap()) } else failed += key
                version++
            }
        }
        return null
    }

    fun retryFailed() { failed.clear(); version++ }

    private fun decode(bytes: ByteArray, px: Int): Bitmap? {
        val o = BitmapFactory.Options(); o.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        val o2 = BitmapFactory.Options(); o2.inSampleSize = s
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
    }

    private fun load(c: Context, song: Song, px: Int): Bitmap? {
        val dl = Store.downloads[song.key]
        if (!song.isOnline || (dl != null && File(dl).exists() && song.art.isEmpty())) {
            val src = if (song.isOnline) Uri.fromFile(File(dl!!)) else Uri.parse(song.uri)
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(c, src)
                val pic = mmr.embeddedPicture
                if (pic != null) decode(pic, px)?.let { return it }
            } catch (_: Exception) {
            } finally { runCatching { mmr.release() } }
            if (!song.isOnline && Build.VERSION.SDK_INT >= 29) {
                try { return c.contentResolver.loadThumbnail(src, android.util.Size(px, px), null) } catch (_: Exception) {}
            }
            if (song.art.isNotEmpty()) {
                try {
                    c.contentResolver.openInputStream(Uri.parse(song.art))?.use { st -> return decode(st.readBytes(), px) }
                } catch (_: Exception) {}
            }
            return null
        }
        if (song.art.isEmpty()) return null
        val url = Online.sized(song.art, px)
        val dir = File(c.cacheDir, "img"); dir.mkdirs()
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val f = File(dir, name)
        var data: ByteArray? = null
        if (f.exists() && f.length() > 100) data = f.readBytes()
        else {
            val req = Request.Builder().url(url).build()
            val resp = Online.client.newCall(req).execute()
            try {
                if (resp.isSuccessful) data = resp.body?.bytes()
            } finally { resp.close() }
            val d = data
            if (d != null) runCatching { f.writeBytes(d) }
        }
        val bytes = data ?: return null
        return decode(bytes, px)
    }

    /** Dominant hue (0..1) of a cover, or null for grey art. */
    fun hue(img: ImageBitmap): Float? {
        val small = Bitmap.createScaledBitmap(img.asAndroidBitmap(), 24, 24, true)
        val px = IntArray(24 * 24)
        small.getPixels(px, 0, 24, 0, 0, 24, 24)
        return app.nilkon.ui.dominantHue(px, 24, 24)
    }
}
