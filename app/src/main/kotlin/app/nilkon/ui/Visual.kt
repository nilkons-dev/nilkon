package app.nilkon.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.nilkon.data.EQ_FREQS
import app.nilkon.playback.Analysis
import app.nilkon.playback.Effects
import app.nilkon.playback.Engine
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * One shared clock for every animated drawing (port of the desktop VisualEngine): smoothed spectrum, record
 * rotation, tonearm, accent colour easing and the playback position estimate.
 */
object V {
    const val N = Analysis.N_BANDS
    const val SPIN_DPS = 200f

    /** Bumped on every frame; Canvas code reads it so it redraws. */
    var frame by mutableIntStateOf(0)

    var t = 0f
    var dt = 0.033f
    val levels = FloatArray(N)
    val peaks = FloatArray(N)
    val hist = ArrayDeque<FloatArray>()
    var bass = 0f
    var energy = 0f
    var playT = 0f
    var armT = 0f
    var spinV = 0f
    var angle = 0f
    var playing = false
    var gain = 1f
    var smooth = 0.5f
    var seed = 1
    var durationMs = 0L
    var reduced = false
    var offsetMs = 0
    val eqDb = FloatArray(N)
    var active = true

    private var posMs = 0.0
    private var posStamp = System.nanoTime()
    private var lastNs = 0L
    private var lastFrameNs = 0L
    private var lastEnginePos = -1L

    fun bandCenter(i: Int): Double = Analysis.F_MIN * (Analysis.F_MAX / Analysis.F_MIN).pow((i + 0.5) / N)

    fun setEq(values: List<Int>, preamp: Int, enabled: Boolean) {
        if (!enabled) { for (i in 0 until N) eqDb[i] = 0f; return }
        val logs = DoubleArray(EQ_FREQS.size) { ln(EQ_FREQS[it].toDouble()) }
        val vals = IntArray(EQ_FREQS.size) { if (it < values.size) values[it] else 0 }
        for (i in 0 until N) {
            val lf = ln(bandCenter(i))
            var g = 0.0
            if (lf <= logs[0]) g = vals[0].toDouble()
            else if (lf >= logs[logs.size - 1]) g = vals[logs.size - 1].toDouble()
            else {
                for (k in 0 until logs.size - 1) {
                    if (logs[k] <= lf && lf <= logs[k + 1]) {
                        val tt = (lf - logs[k]) / (logs[k + 1] - logs[k])
                        g = vals[k] + (vals[k + 1] - vals[k]) * tt
                        break
                    }
                }
            }
            eqDb[i] = (g + preamp).toFloat()
        }
    }

    private fun nowMs(): Double = (System.nanoTime() - posStamp) / 1_000_000.0

    fun positionMs(): Double {
        var ms = if (playing) posMs + nowMs() else posMs
        if (durationMs > 0) ms = min(ms, durationMs.toDouble())
        return ms
    }

    fun progress(): Float = if (durationMs > 0) clamp01((positionMs() / durationMs).toFloat()) else 0f

    private fun setPosition(ms: Long) { posMs = max(0L, ms).toDouble(); posStamp = System.nanoTime() }

    private fun syncPosition(ms: Long) {
        val est = positionMs()
        val err = ms - est
        if (!playing || abs(err) > 450) setPosition(ms)
        else { posMs = est + err * 0.25; posStamp = System.nanoTime() }
    }

    fun synthLevels(tt: Float, sd: Int, out: FloatArray) {
        val bpm = 88 + (sd % 56)
        val beat = 60f / bpm
        val ph = (tt / beat) % 1f
        val kick = exp(-ph * 5f)
        val off = ((tt / beat) + 0.5f) % 1f
        val hat = exp(-off * 9f)
        for (i in 0 until N) {
            val x = i / (N - 1f)
            val low = kick * exp(-((x - 0.08f) / 0.16f).pow(2)) * 0.95f
            var mid = (0.42f + 0.22f * sin(tt * 1.3f + sd * 0.01f + i * 0.7f)) * exp(-((x - 0.45f) / 0.34f).pow(2))
            mid *= 0.65f + 0.35f * sin(tt * 0.7f + x * 6f)
            val high = hat * exp(-((x - 0.85f) / 0.22f).pow(2)) * 0.55f + 0.10f * abs(sin(tt * 9.1f + i * 1.9f)) * x
            out[i] = clamp01(low + mid * 0.85f + high + 0.10f * (1 - x))
        }
    }

    private val raw = FloatArray(N)
    private val synth = FloatArray(N)

    /** Called from the frame clock. */
    fun tick(nowNs: Long) {
        val minGap = if (reduced) 66_000_000L else 30_000_000L
        if (lastFrameNs != 0L && nowNs - lastFrameNs < minGap) return
        lastFrameNs = nowNs
        if (lastNs == 0L) lastNs = nowNs
        val d = min(0.1f, max(0.001f, (nowNs - lastNs) / 1e9f))
        lastNs = nowNs
        if (!active) return
        dt = d
        t += d

        // follow the player
        val p = Engine.positionMs
        val pl = Engine.isPlaying && !Engine.buffering
        if (pl != playing) { posMs = positionMs(); posStamp = System.nanoTime(); playing = pl }
        if (Engine.durationMs > 0) durationMs = Engine.durationMs
        if (p != lastEnginePos) { lastEnginePos = p; syncPosition(p) }

        val step = d * 3f
        playT = clamp01(playT + (if (playing) step else -step))
        val armStep = d * 0.85f
        armT = clamp01(armT + (if (playing) armStep else -armStep * 1.3f))
        val spinTarget = if (playing) SPIN_DPS * clamp01((armT - 0.55f) / 0.45f) else 0f
        val rate = if (spinTarget > spinV) 1.8f else 0.9f
        spinV += (spinTarget - spinV) * min(1f, d * rate)
        angle = (angle + spinV * d) % 360f

        var src: FloatArray? = null
        if (playing) {
            val an = Engine.analysis
            if (an != null) src = an.frameAt(positionMs() + offsetMs)
            if (src == null && Effects.canVisualize && Engine.liveSpectrum) {
                System.arraycopy(Engine.spectrum, 0, raw, 0, N); src = raw
            }
        }
        if (src == null) {
            if (playing) { synthLevels(t, seed, synth); src = synth } else { java.util.Arrays.fill(raw, 0f); src = raw }
        }
        val atk = min(1f, d * 28f)
        val dec = min(1f, d * (22f - 16f * smooth))
        var total = 0f
        val useEq = Engine.analysis != null
        for (i in 0 until N) {
            val v = if (playing) clamp01(src[i] * gain + (if (useEq) eqDb[i] / Analysis.DB_SPAN else 0f)) else 0f
            var cur = levels[i]
            cur += (v - cur) * (if (v > cur) atk else dec)
            levels[i] = cur
            peaks[i] = max(cur, peaks[i] - d * 0.45f)
            total += cur
        }
        energy = total / N
        hist.addLast(levels.copyOf())
        while (hist.size > 16) hist.removeFirst()
        var b = 0f
        for (i in 0 until 5) b += levels[i]
        bass = b / 5f

        if (!T.settled()) T.step(min(1f, d * 4f))
        frame++
    }

    fun levelAt(lv: FloatArray, f: Float): Float {
        val x = clamp01(f) * (N - 1)
        val i = x.toInt()
        val j = min(N - 1, i + 1)
        return lv[i] + (lv[j] - lv[i]) * (x - i)
    }
}
