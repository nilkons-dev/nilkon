package app.nilkon.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Visualizer
import app.nilkon.data.EQ_FREQS
import app.nilkon.data.EqState
import app.nilkon.data.Store
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Equalizer, bass boost, loudness and the real-spectrum visualizer, all attached to the player's audio session. */
object Effects {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var loud: LoudnessEnhancer? = null
    private var vis: Visualizer? = null
    private var session = 0
    private var lastKey: String? = null
    var volumeSetter: ((Float) -> Unit)? = null
        set(v) { field = v; pushVolume() }
    var canVisualize = false

    fun rebuild(sessionId: Int) {
        if (sessionId == 0) return
        release()
        session = sessionId
        runCatching { eq = Equalizer(0, sessionId).also { it.enabled = true } }
        runCatching { bass = BassBoost(0, sessionId).also { it.enabled = true } }
        runCatching { loud = LoudnessEnhancer(sessionId).also { it.enabled = true } }
        tryVisualizer()
        applyFor(lastKey)
    }

    fun tryVisualizer() {
        if (vis != null || session == 0) return
        runCatching {
            vis = Visualizer(session).also {
                it.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                it.enabled = true
            }
            canVisualize = true
        }.onFailure { vis = null; canVisualize = false }
    }

    fun release() {
        runCatching { eq?.release() }; runCatching { bass?.release() }; runCatching { loud?.release() }
        runCatching { vis?.release() }
        eq = null; bass = null; loud = null; vis = null; canVisualize = false
    }

    /** Applies the song's own equalizer, or the global one. */
    fun applyFor(key: String?) {
        lastKey = key
        apply(if (key != null) Store.eqFor(key) else Store.eq)
    }

    private var userVol = 0.75f
    private var volMul = 1f
    private var lastEq: EqState = EqState()
    /** Loudness correction (dB) for the playing song when "Even out loudness" is on. */
    var normalizeDb = 0f
        set(v) { field = v; apply(lastEq) }

    fun setUserVolume(v: Float) { userVol = v.coerceIn(0f, 1f); pushVolume() }

    private fun pushVolume() { volumeSetter?.invoke((userVol * volMul).coerceIn(0f, 1f)) }

    fun apply(s: EqState) {
        lastEq = s
        val e = eq
        if (e != null) runCatching {
            e.enabled = s.enabled
            val range = e.bandLevelRange
            val bands = e.numberOfBands.toInt()
            for (b in 0 until bands) {
                val centre = e.getCenterFreq(b.toShort()) / 1000.0
                val db = if (s.enabled) gainAt(centre, s.values) else 0.0
                val level = (db * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                e.setBandLevel(b.toShort(), level.toShort())
            }
        }
        runCatching { bass?.let { it.enabled = false } }
        // preamp (+ loudness correction): positive -> LoudnessEnhancer, negative -> player volume
        val pre = (if (s.enabled) s.effectivePreamp() else 0f) + (if (Store.normalize) normalizeDb else 0f)
        runCatching {
            loud?.setTargetGain((if (pre > 0) pre * 100 else 0f).toInt())
            loud?.enabled = pre > 0
        }
        volMul = if (pre < 0) 10f.pow(pre / 20f) else 1f
        pushVolume()
    }

    /** Interpolates the 10 user bands (in dB) at the given frequency, on a log-frequency scale. */
    private fun gainAt(hz: Double, v: List<Int>): Double {
        val f = EQ_FREQS
        if (hz <= f.first()) return v.first().toDouble()
        if (hz >= f.last()) return v.last().toDouble()
        for (i in 0 until f.size - 1) {
            if (hz <= f[i + 1]) {
                val t = (ln(hz) - ln(f[i].toDouble())) / (ln(f[i + 1].toDouble()) - ln(f[i].toDouble()))
                return v[i] + (v[i + 1] - v[i]) * t
            }
        }
        return 0.0
    }

    private val fft = ByteArray(1024)
    private var smooth = FloatArray(32)

    /** Fills [out] (0..1) with 32 log-spaced spectrum bars from the audio that is really playing. */
    fun pollSpectrum(out: FloatArray): Boolean? {
        val v = vis ?: return null
        val n = runCatching { v.getFft(fft) }.getOrDefault(Visualizer.ERROR)
        if (n != Visualizer.SUCCESS) return null
        val bins = fft.size / 2 - 1
        val rate = (v.samplingRate / 1000.0).coerceAtLeast(8000.0)
        for (b in out.indices) {
            val lo = (40.0 * (16000.0 / 40.0).pow(b / out.size.toDouble()))
            val hi = (40.0 * (16000.0 / 40.0).pow((b + 1) / out.size.toDouble()))
            val i0 = ((lo / (rate / 2)) * bins).toInt().coerceIn(1, bins)
            val i1 = ((hi / (rate / 2)) * bins).toInt().coerceIn(i0, bins)
            var peak = 0.0
            for (i in i0..i1) {
                val re = fft[2 * i].toInt(); val im = fft[2 * i + 1].toInt()
                peak = maxOf(peak, sqrt((re * re + im * im).toDouble()))
            }
            val db = 20 * log10(peak + 1)
            val x = ((db - 8) / 34).coerceIn(0.0, 1.0).toFloat()
            smooth[b] = if (x > smooth[b]) smooth[b] + (x - smooth[b]) * 0.7f else smooth[b] * 0.86f
            out[b] = smooth[b]
        }
        return true
    }
}
