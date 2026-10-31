package app.nilkon.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.compose.runtime.mutableStateMapOf

/**
 * All user data (playlists, favourites, history, equalizer, downloads, online song info) in one JSON file.
 * Reads and writes happen on the main thread only; the file is written atomically.
 */
object Store {
    private lateinit var file: File
    private var loaded = false

    val playlists = LinkedHashMap<String, MutableList<String>>()
    val liked = LinkedHashSet<String>()
    val history = ArrayList<String>()
    val plays = HashMap<String, Int>()
    val onlineMeta = HashMap<String, Song>()      // every online song the user kept in any list
    val downloads = HashMap<String, String>()     // key -> local file path
    val hidden = HashSet<String>()                // local songs removed from the library view
    val added = LinkedHashMap<String, Song>()     // songs the user added by hand (files / folders)
    val titles = HashMap<String, String>()        // renamed titles
    val trackEq = HashMap<String, EqState>()
    val customPresets = LinkedHashMap<String, EqPreset>()
    var eq = EqState()
    /** Every user setting (same keys as the desktop player). Reading a value from Compose recomposes on change. */
    val settings = mutableStateMapOf<String, Any>()
    val DEFAULTS: Map<String, Any> = mapOf(
        "mode" to "dark", "accent" to "Cat Orange", "adaptive" to false, "background" to "aurora", "custom_bg" to "",
        "bg_dim" to 55, "bg_blur" to 0, "wave_style" to "waves", "reduced_fx" to false, "sync_offset" to 0, "volume" to 75,
        "download_format" to "m4a", "resume_last" to true, "auto_radio" to true, "volume_step" to 5, "seek_step" to 10,
        "start_page" to "home", "toasts" to true, "wave_gain" to 100, "wave_smooth" to 50, "normalize" to false,
        "notif_controls" to true,
    )
    fun bool(k: String): Boolean = (settings[k] ?: DEFAULTS[k]) as? Boolean ?: false
    fun int(k: String): Int = ((settings[k] ?: DEFAULTS[k]) as? Number)?.toInt() ?: 0
    fun str(k: String): String = (settings[k] ?: DEFAULTS[k]) as? String ?: ""
    fun set(k: String, v: Any) { if (DEFAULTS.containsKey(k)) { settings[k] = v; touch() } }
    var normalize: Boolean
        get() = bool("normalize")
        set(v) { set("normalize", v) }
    var radio: Boolean
        get() = bool("auto_radio")
        set(v) { set("auto_radio", v) }
    var lastKey = ""
    var lastPos = 0L

    private var dirty = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "nilkon.json")
        if (loaded) return
        loaded = true
        try {
            if (!file.exists()) return
            val o = JSONObject(file.readText())
            o.optJSONObject("playlists")?.let { p -> p.keys().forEach { n ->
                val a = p.getJSONArray(n); playlists[n] = MutableList(a.length()) { a.getString(it) } } }
            o.optJSONArray("liked")?.let { a -> for (i in 0 until a.length()) liked += a.getString(i) }
            o.optJSONArray("history")?.let { a -> for (i in 0 until a.length()) history += a.getString(i) }
            o.optJSONObject("plays")?.let { p -> p.keys().forEach { plays[it] = p.getInt(it) } }
            o.optJSONArray("online")?.let { a -> for (i in 0 until a.length()) {
                val s = Song.fromJson(a.getJSONObject(i)); onlineMeta[s.key] = s } }
            o.optJSONObject("downloads")?.let { p -> p.keys().forEach { k ->
                if (File(p.getString(k)).exists()) downloads[k] = p.getString(k) } }
            o.optJSONArray("added")?.let { a -> for (i in 0 until a.length()) { val s = Song.fromJson(a.getJSONObject(i)); added[s.key] = s } }
            o.optJSONArray("hidden")?.let { a -> for (i in 0 until a.length()) hidden += a.getString(i) }
            o.optJSONObject("titles")?.let { p -> p.keys().forEach { titles[it] = p.getString(it) } }
            o.optJSONObject("trackEq")?.let { p -> p.keys().forEach { trackEq[it] = EqState.fromJson(p.getJSONObject(it)) } }
            o.optJSONObject("presets")?.let { p -> p.keys().forEach { n ->
                val raw = p.get(n)
                if (raw is JSONArray) customPresets[n] = EqPreset(List(10) { raw.optInt(it).coerceIn(-20, 20) }, 0)
                else if (raw is JSONObject) { val a = raw.optJSONArray("values")
                    customPresets[n] = EqPreset(List(10) { a?.optInt(it, 0)?.coerceIn(-20, 20) ?: 0 }, raw.optInt("preamp").coerceIn(-20, 20)) } } }
            o.optJSONObject("settings")?.let { p -> p.keys().forEach { k ->
                val d = DEFAULTS[k]
                if (d != null) {
                    val v = p.get(k)
                    when (d) {
                        is Boolean -> if (v is Boolean) settings[k] = v
                        is Int -> if (v is Number) settings[k] = v.toInt()
                        is String -> if (v is String) settings[k] = v
                    }
                } } }
            o.optJSONObject("eq")?.let { eq = EqState.fromJson(it) }
            if (o.has("normalize") && !o.has("settings")) { settings["normalize"] = o.optBoolean("normalize", false); settings["auto_radio"] = o.optBoolean("radio", true) }
            lastKey = o.optString("lastKey"); lastPos = o.optLong("lastPos")
        } catch (_: Exception) {
            // A damaged file must never stop the app; keep a copy and start clean.
            runCatching { file.copyTo(File(file.parentFile, "nilkon.json.bad"), true) }
        }
    }

    fun dataPath(): String = if (this::file.isInitialized) file.path else ""

    fun touch() {
        dirty = true
        handler.removeCallbacks(flush)
        handler.postDelayed(flush, 600)
    }

    private val flush = Runnable { save() }

    fun save() {
        if (!dirty) return
        dirty = false
        runCatching {
            prune()
            val o = JSONObject()
            o.put("playlists", JSONObject().also { p -> playlists.forEach { (n, l) -> p.put(n, JSONArray(l)) } })
            o.put("liked", JSONArray(liked.toList()))
            o.put("history", JSONArray(history.takeLast(300)))
            o.put("plays", JSONObject(plays as Map<*, *>))
            o.put("online", JSONArray().also { a -> onlineMeta.values.forEach { a.put(it.toJson()) } })
            o.put("downloads", JSONObject(downloads as Map<*, *>))
            o.put("hidden", JSONArray(hidden.toList()))
            o.put("added", JSONArray().also { a -> added.values.forEach { a.put(it.toJson()) } })
            o.put("titles", JSONObject(titles as Map<*, *>))
            o.put("trackEq", JSONObject().also { p -> trackEq.forEach { (k, v) -> p.put(k, v.toJson()) } })
            o.put("presets", JSONObject().also { p -> customPresets.forEach { (n, v) ->
                p.put(n, JSONObject().put("values", JSONArray(v.values)).put("preamp", v.preamp)) } })
            o.put("settings", JSONObject().also { p -> settings.forEach { (k, v) -> p.put(k, v) } })
            o.put("eq", eq.toJson())
            o.put("lastKey", lastKey); o.put("lastPos", lastPos)
            val tmp = File(file.parentFile, "nilkon.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }

    /** Keep online song info only for songs that are still in a list, history or downloads. */
    private fun prune() {
        val used = HashSet<String>()
        used += liked; used += history.takeLast(300); used += downloads.keys
        playlists.values.forEach { used += it }
        onlineMeta.keys.retainAll(used)
    }

    // ----- actions -----------------------------------------------------------------------
    fun remember(s: Song) { if (s.isOnline) onlineMeta[s.key] = s }

    fun toggleLike(s: Song): Boolean {
        remember(s)
        val now = if (s.key in liked) { liked -= s.key; false } else { liked += s.key; true }
        touch(); return now
    }

    fun createPlaylist(name: String): Boolean {
        val n = name.trim().take(60)
        if (n.isEmpty() || n in playlists) return false
        playlists[n] = mutableListOf(); touch(); return true
    }

    fun renamePlaylist(old: String, name: String): Boolean {
        val n = name.trim().take(60)
        if (n.isEmpty() || n in playlists || old !in playlists) return false
        val rebuilt = LinkedHashMap<String, MutableList<String>>()
        playlists.forEach { (k, v) -> rebuilt[if (k == old) n else k] = v }
        playlists.clear(); playlists.putAll(rebuilt); touch(); return true
    }

    fun deletePlaylist(name: String) { playlists.remove(name); touch() }

    fun addToPlaylist(name: String, s: Song): Boolean {
        val l = playlists[name] ?: return false
        remember(s)
        if (s.key in l) return false
        l += s.key; touch(); return true
    }

    fun removeFromPlaylist(name: String, key: String) { playlists[name]?.remove(key); touch() }

    fun movePlaylistItem(name: String, from: Int, to: Int): Boolean {
        val l = playlists[name] ?: return false
        if (from !in l.indices || to !in l.indices) return false
        l.add(to, l.removeAt(from)); touch(); return true
    }

    /** Moves a liked song one place in the list as it is shown (newest first). */
    fun moveLiked(key: String, dir: Int): Boolean {
        val l = liked.toMutableList()
        val i = l.indexOf(key)
        val j = i - dir                      // the list is shown reversed
        if (i < 0 || j !in l.indices) return false
        l.add(j, l.removeAt(i))
        liked.clear(); liked.addAll(l); touch(); return true
    }

    fun recordPlay(s: Song) {
        remember(s)
        history.remove(s.key); history += s.key
        plays[s.key] = (plays[s.key] ?: 0) + 1
        touch()
    }

    fun setTrackEq(key: String, eq: EqState?) { if (eq == null) trackEq.remove(key) else trackEq[key] = eq; touch() }
    fun eqFor(key: String): EqState = trackEq[key] ?: eq
}
