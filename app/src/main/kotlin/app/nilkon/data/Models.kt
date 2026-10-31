package app.nilkon.data

import org.json.JSONObject

/** One playable song. Local songs: key "local:<id>", online songs: key "yt:<videoId>". */
data class Song(
    val key: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val uri: String = "",
    val art: String = "",
) {
    val isOnline get() = key.startsWith("yt:")
    val videoId get() = key.removePrefix("yt:")

    fun toJson(): JSONObject = JSONObject()
        .put("key", key).put("title", title).put("artist", artist).put("album", album)
        .put("dur", durationMs).put("uri", uri).put("art", art)

    companion object {
        fun fromJson(o: JSONObject) = Song(
            o.getString("key"), o.optString("title"), o.optString("artist"), o.optString("album"),
            o.optLong("dur"), o.optString("uri"), o.optString("art"),
        )
        fun online(id: String, title: String, artist: String, album: String, dur: Long, art: String) =
            Song("yt:$id", title, artist, album, dur, "nilkon://yt/$id", art)
    }
}

/** 10-band equalizer setting (same bands as the desktop player). */
data class EqState(
    val values: List<Int> = List(10) { 0 },
    val preamp: Int = 0,
    val enabled: Boolean = true,
    val safe: Boolean = true,
    val bass: Int = 0,        // 0..1000 BassBoost strength
    val preset: String = "Flat",
) {
    /** Preamp actually applied: smart headroom lowers the level when bands are boosted. */
    fun effectivePreamp(): Float {
        var p = preamp.toFloat()
        if (safe && enabled) p -= maxOf(0, values.maxOrNull() ?: 0) * 0.55f
        return p.coerceIn(-20f, 20f)
    }
    fun toJson(): JSONObject = JSONObject()
        .put("values", org.json.JSONArray(values)).put("preamp", preamp).put("enabled", enabled)
        .put("safe", safe).put("bass", bass).put("preset", preset)
    companion object {
        fun fromJson(o: JSONObject): EqState {
            val a = o.optJSONArray("values")
            val v = List(10) { i -> (a?.optInt(i, 0) ?: 0).coerceIn(-20, 20) }
            return EqState(v, o.optInt("preamp").coerceIn(-20, 20), o.optBoolean("enabled", true),
                o.optBoolean("safe", true), o.optInt("bass").coerceIn(0, 1000), o.optString("preset", "Custom"))
        }
    }
}

/** A saved equalizer preset of the user. */
data class EqPreset(val values: List<Int>, val preamp: Int = 0)

val EQ_FREQS = intArrayOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)

val EQ_PRESETS: LinkedHashMap<String, List<Int>> = linkedMapOf(
    "Flat" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    "Bass Boost" to listOf(8, 7, 5, 2, 0, 0, 1, 2, 2, 2),
    "Bass Reducer" to listOf(-7, -6, -4, -1, 0, 0, 0, 0, 0, 0),
    "Treble Boost" to listOf(0, 0, 0, 1, 2, 4, 6, 7, 8, 8),
    "Treble Reducer" to listOf(0, 0, 0, 0, 0, -2, -4, -6, -8, -8),
    "Vocal" to listOf(-2, -1, 1, 3, 4, 3, 1, -1, -2, -2),
    "Night" to listOf(3, 2, 0, -2, -1, 2, 3, 2, 2, 2),
    "Rock" to listOf(5, 3, -2, -3, 1, 3, 5, 5, 5, 5),
    "Electronic" to listOf(5, 4, 1, 0, -2, 2, 4, 5, 5, 5),
    "Pop" to listOf(-1, 2, 4, 5, 3, 0, -1, -1, -1, -1),
    "Jazz" to listOf(4, 3, 1, 2, -2, -2, 0, 1, 3, 4),
    "Classical" to listOf(5, 4, 3, 2, -1, -1, 0, 2, 3, 4),
    "Hip-Hop" to listOf(5, 4, 1, 3, -1, -1, 1, -1, 2, 3),
    "R&B" to listOf(3, 7, 6, 1, -2, -1, 2, 2, 3, 3),
    "Acoustic" to listOf(5, 4, 3, 1, 2, 2, 3, 4, 3, 2),
    "Dance" to listOf(6, 5, 2, 0, 0, -3, -5, -5, 0, 0),
    "Metal" to listOf(4, 3, 0, -1, -3, 0, 3, 5, 4, 3),
    "Latin" to listOf(4, 2, 0, 0, -2, -2, -2, 0, 4, 6),
    "Piano" to listOf(3, 2, 0, 3, 4, 2, 4, 5, 3, 4),
    "Lounge" to listOf(-3, -1, 2, 4, 2, -2, -1, 1, 2, 1),
    "Deep Bass" to listOf(6, 5, 3, 1, 0, -2, -4, -5, -6, -7),
    "Loudness" to listOf(6, 4, 0, 0, -2, 0, -1, -5, 5, 1),
    "Small Speakers" to listOf(5, 4, 3, 2, 1, 0, -1, -2, -3, -3),
    "Spoken Word" to listOf(-4, -1, 0, 1, 3, 5, 5, 4, 2, 0),
)
