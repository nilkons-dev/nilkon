package app.nilkon.online

import android.content.Context
import app.nilkon.data.AppLog
import app.nilkon.data.Song
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Online music: song search and "similar songs" come from the music catalogue's public web endpoint,
 * stream addresses are resolved with NewPipeExtractor. All functions block - call them off the main thread.
 */
object Online {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    private const val SONGS_FILTER = "EgWKAQIIAWoMEA4QChADEAQQCRAF"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true).build()

    private class Resolved(val at: Long, val url: String, val ua: String)
    private val urlCache = ConcurrentHashMap<String, Resolved>()
    private const val TTL_MS = 20 * 60 * 1000L
    @Volatile private var ready = false

    fun init(ctx: Context) {
        if (ready) return
        NewPipe.init(NpDownloader(client))
        ready = true
    }

    // ---------------------------------------------------------------------------- catalogue
    private fun context(): JSONObject = JSONObject().put("client", JSONObject()
        .put("clientName", "WEB_REMIX").put("clientVersion", "1.20241009.01.00").put("hl", "en").put("gl", "US"))

    private fun post(url: String, body: JSONObject): JSONObject {
        val req = Request.Builder().url(url)
            .header("User-Agent", UA).header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("The music service answered ${r.code}")
            return JSONObject(r.body?.string() ?: throw IOException("Empty answer"))
        }
    }

    private val urlIdRegex = Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/|embed/|live/)|music\\.youtube\\.com/watch\\?(?:.*&)?v=)([A-Za-z0-9_-]{11})")

    /** The song id inside a pasted song link, or null when [text] is not a link. */
    fun videoIdFromUrl(text: String): String? {
        val t = text.trim()
        if (!t.startsWith("http", ignoreCase = true) && !t.contains("youtu")) return null
        return urlIdRegex.find(t)?.groupValues?.get(1)
    }

    /** One song from its id (used when a link is pasted into the search box). */
    fun lookup(videoId: String): Song {
        var first: Throwable? = null
        // 1) the music catalogue itself (same endpoint that already powers search and "similar songs")
        try {
            val body = JSONObject().put("context", context()).put("videoId", videoId)
                .put("playlistId", "RDAMVM$videoId").put("params", "wAEB")
            val l = parse(post(NEXT_URL, body), 5)
            (l.firstOrNull { it.key == "yt:$videoId" } ?: l.firstOrNull())?.let { if (it.videoId == videoId) return it }
        } catch (e: Throwable) { first = e }
        // 2) public mirror
        try {
            val j = pipedCall("streams/$videoId")
            val t = j.optString("title")
            if (t.isNotEmpty()) return Song.online(videoId, t, j.optString("uploader").removeSuffix(" - Topic"), "",
                j.optLong("duration") * 1000L, j.optString("thumbnailUrl"))
        } catch (e: Throwable) { if (first == null) first = e }
        // 3) player endpoints
        for (c in listOf(VR, ANDROID_MUSIC)) {
            try {
                val d = playerCall(videoId, c).optJSONObject("videoDetails")
                if (d != null && d.optString("title").isNotEmpty()) {
                    val th = d.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val art = if (th != null && th.length() > 0) th.getJSONObject(th.length() - 1).optString("url") else ""
                    return Song.online(videoId, d.optString("title"), d.optString("author").removeSuffix(" - Topic"), "",
                        d.optString("lengthSeconds").toLongOrNull()?.times(1000L) ?: 0L, art)
                }
            } catch (e: Throwable) { if (first == null) first = e }
        }
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val art = info.thumbnails.maxByOrNull { it.height }?.url ?: ""
            return Song.online(videoId, info.name ?: "Unknown", (info.uploaderName ?: "").removeSuffix(" - Topic"), "",
                (if (info.duration > 0) info.duration else 0L) * 1000L, art)
        } catch (e: Throwable) { throw first ?: e }
    }

    private val sizeRegex = Regex("=w\\d+-h\\d+.*$")

    /** Asks the image server for a smaller picture when the address allows it. */
    fun sized(url: String, px: Int): String {
        if ((url.contains("googleusercontent.com") || url.contains("ggpht.com")) && sizeRegex.containsMatchIn(url))
            return url.replace(sizeRegex, "=w$px-h$px-l90-rj")
        return url
    }

    fun isNetworkError(e: Throwable): Boolean {
        var x: Throwable? = e
        while (x != null) {
            if (x is java.net.UnknownHostException || x is java.net.ConnectException || x is java.net.SocketTimeoutException) return true
            x = x.cause
        }
        return false
    }

    fun search(query: String, limit: Int = 25): List<Song> {
        val q = query.trim()
        videoIdFromUrl(q)?.let { return listOf(lookup(it)) }
        if (q.length < 2) return emptyList()
        val body = JSONObject().put("context", context()).put("query", q).put("params", SONGS_FILTER)
        return parse(post(SEARCH_URL, body), limit)
    }

    /** Songs that play well after [seed] - used to keep the music going (radio). */
    fun related(seed: Song, limit: Int = 30): List<Song> {
        val id = seed.videoId
        val body = JSONObject().put("context", context()).put("videoId", id)
            .put("playlistId", "RDAMVM$id").put("params", "wAEB")
        return parse(post(NEXT_URL, body), limit + 1).filter { it.key != seed.key }.take(limit)
    }

    private fun parse(root: JSONObject, limit: Int): List<Song> {
        val out = ArrayList<Song>()
        val seen = HashSet<String>()
        fun add(s: Song?) { if (s != null && seen.add(s.key) && out.size < limit) out += s }
        fun walk(n: Any?) {
            if (out.size >= limit) return
            when (n) {
                is JSONObject -> {
                    val it = n.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        val v = n.opt(k)
                        when (k) {
                            "musicResponsiveListItemRenderer" -> if (v is JSONObject) add(listItem(v))
                            "playlistPanelVideoRenderer" -> if (v is JSONObject) add(panelItem(v))
                            else -> walk(v)
                        }
                    }
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(root)
        return out
    }

    private fun runs(o: JSONObject?): List<String> {
        val a = o?.optJSONArray("runs") ?: return emptyList()
        return List(a.length()) { a.getJSONObject(it).optString("text") }
    }

    private fun findId(n: Any?): String? {
        when (n) {
            is JSONObject -> {
                n.optString("videoId").takeIf { it.length == 11 }?.let { return it }
                val it = n.keys()
                while (it.hasNext()) findId(n.opt(it.next()))?.let { r -> return r }
            }
            is JSONArray -> for (i in 0 until n.length()) findId(n.opt(i))?.let { return it }
        }
        return null
    }

    private fun lastThumb(n: JSONObject): String {
        var best = ""
        fun walk(x: Any?) {
            when (x) {
                is JSONObject -> {
                    x.optJSONArray("thumbnails")?.let { a -> if (a.length() > 0) best = a.getJSONObject(a.length() - 1).optString("url") }
                    val it = x.keys(); while (it.hasNext()) walk(x.opt(it.next()))
                }
                is JSONArray -> for (i in 0 until x.length()) walk(x.opt(i))
            }
        }
        walk(n)
        return best.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544")
    }

    private fun dur(s: String): Long? {
        val m = Regex("^(?:(\\d+):)?(\\d{1,2}):(\\d{2})$").find(s.trim()) ?: return null
        val h = m.groupValues[1].ifEmpty { "0" }.toLong()
        return ((h * 60 + m.groupValues[2].toLong()) * 60 + m.groupValues[3].toLong()) * 1000
    }

    private fun listItem(o: JSONObject): Song? {
        val id = o.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.length == 11 } ?: findId(o) ?: return null
        val cols = o.optJSONArray("flexColumns") ?: return null
        fun col(i: Int) = runs(cols.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text"))
        val title = col(0).joinToString("").trim()
        if (title.isEmpty()) return null
        val groups = ArrayList<String>()
        var cur = StringBuilder()
        for (r in col(1)) { if (r.trim() == "•") { groups += cur.toString().trim(); cur = StringBuilder() } else cur.append(r) }
        groups += cur.toString().trim()
        groups.removeAll { it.isEmpty() || it == "Song" || it == "Video" }
        var ms = 0L
        groups.lastOrNull()?.let { l -> dur(l)?.let { ms = it; groups.removeAt(groups.size - 1) } }
        if (ms in 1..45_000 || ms > 20 * 60_000) return null   // not a normal song
        return Song.online(id, title, groups.getOrNull(0) ?: "", groups.getOrNull(1) ?: "", ms, lastThumb(o))
    }

    private fun panelItem(o: JSONObject): Song? {
        val id = o.optString("videoId").takeIf { it.length == 11 } ?: return null
        val title = runs(o.optJSONObject("title")).joinToString("").trim().ifEmpty { return null }
        val by = runs(o.optJSONObject("longBylineText")).joinToString("").split("•").map { it.trim() }.filter { it.isNotEmpty() }
        val ms = dur(runs(o.optJSONObject("lengthText")).joinToString("")) ?: 0
        if (ms in 1..45_000 || ms > 20 * 60_000) return null
        return Song.online(id, title, by.getOrNull(0) ?: "", by.getOrNull(1) ?: "", ms, lastThumb(o))
    }

    // ---------------------------------------------------------------------------- streaming
    private class Client(val name: String, val id: String, val version: String, val ua: String, val embed: Boolean = false, val extra: JSONObject.() -> Unit)

    private val VR = Client("ANDROID_VR", "28", "1.60.19",
        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip") {
        put("deviceMake", "Oculus"); put("deviceModel", "Quest 3"); put("osName", "Android"); put("osVersion", "12L")
        put("androidSdkVersion", 32)
    }
    private val ANDROID = Client("ANDROID", "3", "19.44.38",
        "com.google.android.youtube/19.44.38 (Linux; U; Android 11) gzip") {
        put("osName", "Android"); put("osVersion", "11"); put("androidSdkVersion", 30)
    }
    private val ANDROID_MUSIC = Client("ANDROID_MUSIC", "21", "7.27.52",
        "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip") {
        put("osName", "Android"); put("osVersion", "11"); put("androidSdkVersion", 30)
    }
    private val TV_EMBED = Client("TVHTML5_SIMPLY_EMBEDDED_PLAYER", "85", "2.0",
        "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15", true) { }
    private val IOS = Client("IOS", "5", "20.10.4",
        "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)") {
        put("deviceMake", "Apple"); put("deviceModel", "iPhone16,2"); put("osName", "iPhone"); put("osVersion", "18.3.2.22D82")
    }

    private val PIPED = listOf("https://pipedapi.kavin.rocks", "https://api.piped.private.coffee", "https://pipedapi.adminforge.de")

    /** Asks the public mirrors in turn; the first good answer wins. */
    private fun pipedCall(path: String): JSONObject {
        var err: Throwable? = null
        for (host in PIPED) {
            try {
                val req = Request.Builder().url("$host/$path").header("User-Agent", UA).build()
                val c = client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
                c.newCall(req).execute().use { r ->
                    if (r.isSuccessful) {
                        val j = JSONObject(r.body?.string() ?: "{}")
                        if (j.optString("error").isEmpty()) return j
                    }
                }
            } catch (e: Throwable) { err = e }
        }
        throw err ?: IOException("Mirror unavailable")
    }

    private fun viaPiped(videoId: String): Resolved {
        val a = pipedCall("streams/$videoId").optJSONArray("audioStreams") ?: throw IOException("No audio stream found for this song")
        var best: JSONObject? = null
        var score = -1L
        for (i in 0 until a.length()) {
            val f = a.getJSONObject(i)
            if (f.optString("url").isEmpty()) continue
            val sc = f.optLong("bitrate") + if (f.optString("mimeType").contains("mp4")) 1_000_000_000L else 0L
            if (sc > score) { score = sc; best = f }
        }
        return Resolved(System.currentTimeMillis(), best?.optString("url") ?: throw IOException("No audio stream found for this song"), UA)
    }

    private fun playerCall(videoId: String, c: Client): JSONObject {
        val cl = JSONObject().put("clientName", c.name).put("clientVersion", c.version)
            .put("hl", "en").put("gl", "US").put("userAgent", c.ua)
        c.extra(cl)
        val ctx = JSONObject().put("client", cl)
        if (c.embed) ctx.put("thirdParty", JSONObject().put("embedUrl", "https://www.youtube.com/"))
        val body = JSONObject().put("context", ctx).put("videoId", videoId)
            .put("contentCheckOk", true).put("racyCheckOk", true)
        val req = Request.Builder().url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", c.ua).header("X-YouTube-Client-Name", c.id).header("X-YouTube-Client-Version", c.version)
            .header("Origin", "https://www.youtube.com")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("The music service answered ${r.code}")
            return JSONObject(r.body?.string() ?: throw IOException("Empty answer"))
        }
    }

    private fun viaPlayer(videoId: String, c: Client): Resolved {
        val j = playerCall(videoId, c)
        val st = j.optJSONObject("playabilityStatus")
        val status = st?.optString("status") ?: ""
        AppLog.add("player-api", "${c.name}: status=${status.ifEmpty { "none" }} reason=${st?.optString("reason") ?: ""} formats=${j.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")?.length() ?: 0}")
        if (status.isNotEmpty() && status != "OK")
            throw IOException(st?.optString("reason")?.ifEmpty { null } ?: "This song can't be played ($status)")
        val fmts = j.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: throw IOException("No audio stream found for this song")
        var best: JSONObject? = null
        var bestScore = -1L
        for (i in 0 until fmts.length()) {
            val f = fmts.getJSONObject(i)
            val mime = f.optString("mimeType")
            if (!mime.startsWith("audio/") || f.optString("url").isEmpty()) continue
            val score = f.optLong("bitrate") + if (mime.contains("mp4a")) 1_000_000_000L else 0L   // AAC plays everywhere
            if (score > bestScore) { bestScore = score; best = f }
        }
        val url = best?.optString("url") ?: throw IOException("No audio stream found for this song")
        return Resolved(System.currentTimeMillis(), url, c.ua)
    }

    private fun viaNewPipe(videoId: String): Resolved {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val audio = info.audioStreams.filter { it.content.isNotEmpty() && it.isUrl }
        val pick = audio.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: audio.maxByOrNull { it.averageBitrate }
            ?: throw IOException("No audio stream found for this song")
        return Resolved(System.currentTimeMillis(), pick.content, UA)
    }

    /** Host and a few harmless parameters of a stream address (no signatures), for the problem log. */
    fun describe(url: String): String {
        val host = url.substringAfter("://").substringBefore('/').substringBefore('?')
        fun q(k: String) = Regex("[?&]$k=([^&]*)").find(url)?.groupValues?.get(1)
        val parts = listOfNotNull(q("c")?.let { "c=$it" }, q("clen")?.let { "clen=$it" }, q("mime")?.let { "mime=$it" },
            if (url.contains("&range=")) "range-param" else null, q("expire")?.let { "expire=$it" })
        return host + (if (parts.isEmpty()) "" else " (" + parts.joinToString(", ") + ")")
    }

    private val clenRegex = Regex("[?&]clen=(\\d+)")

    /** Size of the stream when the address carries it (video servers want the range inside the address, not as a header). */
    fun streamLength(url: String): Long? =
        if (url.contains("googlevideo.com")) clenRegex.find(url)?.groupValues?.get(1)?.toLongOrNull() else null

    fun rangedUrl(url: String, from: Long, to: Long): String = "$url&range=$from-$to"

    /** Asks for the first bytes of the stream, so an address the server would refuse is dropped before it is used. */
    private fun probe(r: Resolved) {
        val len = streamLength(r.url)
        val b = Request.Builder().header("User-Agent", r.ua)
        val req = (if (len != null) b.url(rangedUrl(r.url, 0, 1)) else b.url(r.url).header("Range", "bytes=0-1")).build()
        val c = client.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
        c.newCall(req).execute().use { resp ->
            AppLog.add("probe", "answer ${resp.code} ${resp.message} content-type=${resp.header("Content-Type")} length=${resp.header("Content-Length")}")
            if (resp.code != 200 && resp.code != 206) throw IOException("Stream refused (${resp.code})")
        }
    }

    private fun resolveFull(videoId: String): Resolved {
        urlCache[videoId]?.let { if (System.currentTimeMillis() - it.at < TTL_MS) return it }
        val steps = listOf<Pair<String, () -> Resolved>>(
            "VR" to { viaPlayer(videoId, VR) }, "Music" to { viaPlayer(videoId, ANDROID_MUSIC) }, "TV" to { viaPlayer(videoId, TV_EMBED) },
            "iOS" to { viaPlayer(videoId, IOS) }, "Android" to { viaPlayer(videoId, ANDROID) }, "Mirror" to { viaPiped(videoId) },
            "NewPipe" to { viaNewPipe(videoId) })
        val notes = ArrayList<String>()
        for ((name, step) in steps) {
            try {
                val r = step()
                AppLog.add("resolve", "$videoId $name: got address ${describe(r.url)}")
                probe(r)
                AppLog.add("resolve", "$videoId $name: server accepted the address")
                urlCache[videoId] = r; return r
            }
            catch (e: Throwable) {
                AppLog.add("resolve", "$videoId $name: failed", e)
                if (isNetworkError(e)) throw e
                val m = e.message ?: e.javaClass.simpleName
                notes += name + " " + when {
                    m.contains("not a bot", true) || m.contains("Sign in", true) -> "bot-check"
                    m.contains("Stream refused") -> m.substringAfter("Stream refused ").trim('(', ')')
                    else -> m.take(24)
                }
            }
        }
        AppLog.add("resolve", "$videoId: every method failed - " + notes.joinToString(" | "))
        throw IOException("All methods failed: " + notes.joinToString(" | "))
    }

    /** Address of the best audio stream for [videoId]. Cached for 20 minutes. */
    fun resolve(videoId: String): String = resolveFull(videoId).url

    /** The User-Agent the stream address must be requested with. */
    fun userAgentFor(videoId: String): String = urlCache[videoId]?.ua ?: UA

    fun forget(videoId: String) { urlCache.remove(videoId) }

    /** Drops every remembered stream address so the next play asks for fresh ones. */
    fun clearCache() { urlCache.clear() }

    fun friendlyError(e: Throwable): String = when {
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException -> "No internet connection"
        (e.message ?: "").startsWith("All methods failed") -> e.message!!.take(220)
        (e.message ?: "").contains("not a bot", true) || (e.message ?: "").contains("Sign in", true) ->
            "YouTube wants a check on this connection - try again in a minute or switch Wi-Fi / mobile data"
        e is IOException && (e.message ?: "").contains("429") -> "Too many requests - try again in a minute"
        else -> e.message?.take(120) ?: "Something went wrong"
    }

    // ---------------------------------------------------------------------------- download
    private fun safeName(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ").replace(Regex("\\s+"), " ").trim().take(90)

    /** Downloads the song into [dir]. Returns the file. [progress] gets 0..1. Throws on failure. */
    fun download(song: Song, dir: File, progress: (Float) -> Unit, cancelled: () -> Boolean): File {
        dir.mkdirs()
        val url = resolve(song.videoId)
        val ua = userAgentFor(song.videoId)
        val target = File(dir, safeName("${song.artist} - ${song.title}".trim(' ', '-')).ifEmpty { song.videoId } + ".m4a")
        val part = File(dir, target.name + ".part")
        try {
            part.outputStream().use { out ->
                var start = 0L
                val clen = streamLength(url)
                var total = clen ?: -1L
                val chunk = 3L * 1024 * 1024
                while (total < 0 || start < total) {
                    if (cancelled()) throw IOException("Cancelled")
                    val req = (if (clen != null) Request.Builder().url(rangedUrl(url, start, minOf(start + chunk - 1, clen - 1))).header("User-Agent", ua)
                        else Request.Builder().url(url).header("User-Agent", ua).header("Range", "bytes=$start-${start + chunk - 1}")).build()
                    client.newCall(req).execute().use { r ->
                        if (r.code != 200 && r.code != 206) { forget(song.videoId); throw IOException("Download refused (${r.code})") }
                        if (total < 0) {
                            total = r.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: r.body?.contentLength() ?: -1L
                            if (total < 0) throw IOException("Unknown file size")
                        }
                        val body = r.body ?: throw IOException("Empty answer")
                        val got = body.byteStream().copyTo(out)
                        if (got == 0L) throw IOException("Empty answer")
                        start += got
                        if (r.code == 200 && clen == null) start = total          // server ignored the range: whole file received
                    }
                    progress((start.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            target.delete()
            if (!part.renameTo(target)) throw IOException("Could not save the file")
            return target
        } catch (e: Throwable) {
            part.delete(); throw e
        }
    }
}

/** NewPipeExtractor needs an HTTP client; this one uses OkHttp. */
class NpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val method = request.httpMethod()
        val data = request.dataToSend()
        val body = when {
            data != null -> data.toRequestBody(null)
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        val b = Request.Builder().url(request.url()).method(method, body)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
        request.headers().forEach { (k, vs) -> b.removeHeader(k); vs.forEach { v -> b.addHeader(k, v) } }
        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("Too many requests", request.url())
            return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
        }
    }
}
