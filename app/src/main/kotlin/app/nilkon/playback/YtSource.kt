package app.nilkon.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import app.nilkon.data.AppLog
import app.nilkon.online.Online
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

/**
 * Data source for online songs ("nilkon://yt/<id>"). It resolves the real stream address and then reads the song in
 * requests of the size the server accepted (the video servers refuse big requests for some addresses). Anything that is
 * not an online song, or an address that cannot be read in ranges, goes to [fallback].
 */
class YtSource(private val fallback: DataSource) : DataSource {
    private var delegate: DataSource? = null
    private var openUri: Uri? = null
    private var stream: Online.Stream? = null
    private var pos = 0L
    private var end = 0L
    private var left = 0L
    private var response: Response? = null
    private var input: InputStream? = null

    override fun addTransferListener(transferListener: TransferListener) { fallback.addTransferListener(transferListener) }

    override fun open(dataSpec: DataSpec): Long {
        openUri = dataSpec.uri
        if (dataSpec.uri.scheme != "nilkon") { delegate = fallback; return fallback.open(dataSpec) }
        val id = dataSpec.uri.lastPathSegment ?: throw IOException("Bad song address")
        val s = Online.stream(id)
        stream = s
        if (s.length <= 0L || s.chunk <= 0L) {
            delegate = fallback
            return fallback.open(dataSpec.buildUpon().setUri(Uri.parse(s.url)).setHttpRequestHeaders(mapOf("User-Agent" to s.ua)).build())
        }
        delegate = null
        pos = dataSpec.position
        if (pos >= s.length) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        val remaining = s.length - pos
        val size = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, remaining) else remaining
        end = pos + size
        left = 0L
        AppLog.add("player", "reading $id from byte $pos in ${s.chunk / 1024} KB requests")
        return size
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        delegate?.let { return it.read(buffer, offset, length) }
        if (length == 0) return 0
        if (pos >= end) return C.RESULT_END_OF_INPUT
        if (input == null || left <= 0L) nextChunk()
        val want = minOf(length.toLong(), left, end - pos).toInt()
        val n = input!!.read(buffer, offset, want)
        if (n < 0) throw IOException("The song stream ended early")
        pos += n; left -= n
        if (left <= 0L) closeChunk()
        return n
    }

    private fun nextChunk() {
        closeChunk()
        val s = stream ?: throw IOException("Not opened")
        val to = minOf(pos + s.chunk - 1, end - 1)
        val req = Request.Builder().url(Online.rangedUrl(s.url, pos, to)).header("User-Agent", s.ua).build()
        val r = Online.client.newCall(req).execute()
        if (r.code != 200 && r.code != 206) {
            val c = r.code
            r.close()
            AppLog.add("player", "chunk $pos-$to refused with $c")
            throw IOException("Response code: $c")
        }
        response = r
        input = r.body?.byteStream() ?: throw IOException("Empty answer")
        left = to - pos + 1
    }

    private fun closeChunk() {
        try { input?.close() } catch (_: Exception) {}
        try { response?.close() } catch (_: Exception) {}
        input = null; response = null; left = 0L
    }

    override fun getUri(): Uri? = delegate?.uri ?: openUri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        val d = delegate
        delegate = null
        if (d != null) d.close() else closeChunk()
    }
}
