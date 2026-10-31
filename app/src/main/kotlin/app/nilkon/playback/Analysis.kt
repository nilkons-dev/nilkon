package app.nilkon.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spectrum timeline of one song, computed from the decoded audio (port of the desktop audio_analysis.py).
 * Frames appear while the song is being decoded; [frameAt] gives the band levels at a playback position.
 */
class Analysis(sampleRate: Int) {
    companion object {
        const val N_BANDS = 32
        const val F_MIN = 40.0
        const val F_MAX = 16000.0
        const val N_FFT = 2048
        const val HOP = 1024
        const val DB_FLOOR = -75f
        const val DB_SPAN = 70f
        const val ENV_BINS = 480
    }

    @Volatile var sr: Int = if (sampleRate > 0) sampleRate else 44100
        private set
    @Volatile var done = false
        private set
    @Volatile var envelope: FloatArray? = null
        private set
    @Volatile var failed = false
    @Volatile var cancelled = false
    /** Average loudness of the song in dBFS (RMS), NaN until the analysis is finished. */
    @Volatile var loudnessDb = Float.NaN
        private set

    private val window = FloatArray(N_FFT) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (N_FFT - 1))).toFloat() }
    private val tilt = FloatArray(N_BANDS) { it * 0.35f }
    private var edges = bandEdges()
    private val lock = Any()
    private var frames = ArrayList<FloatArray>()
    private var rms = FloatArray(4096)
    private var rmsN = 0
    private var buf = FloatArray(N_FFT * 4)
    private var bufLen = 0
    private val re = DoubleArray(N_FFT)
    private val im = DoubleArray(N_FFT)
    private val cosT = DoubleArray(N_FFT / 2) { cos(2.0 * PI * it / N_FFT) }
    private val sinT = DoubleArray(N_FFT / 2) { -sin(2.0 * PI * it / N_FFT) }
    private var prov: FloatArray? = null
    private var provN = 0

    fun setSampleRate(rate: Int) {
        if (rate > 0 && rate != sr) { sr = rate; edges = bandEdges() }
    }

    private fun bandEdges(): IntArray {
        val nb = N_FFT / 2 + 1
        val bins = IntArray(N_BANDS + 1)
        for (i in 0..N_BANDS) {
            val hz = F_MIN * (F_MAX / F_MIN).pow(i.toDouble() / N_BANDS)
            bins[i] = Math.round(hz * N_FFT / sr.toDouble()).toInt().coerceIn(1, nb - 1)
        }
        for (i in 1..N_BANDS) if (bins[i] <= bins[i - 1]) bins[i] = min(bins[i - 1] + 1, nb)
        return bins
    }

    private fun fft() {
        val n = N_FFT
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                var k = 0
                for (m in 0 until half) {
                    val wr = cosT[k]; val wi = sinT[k]
                    val a = i + m; val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Adds mono samples (-1..1). */
    fun feed(mono: FloatArray, count: Int) {
        if (count <= 0) return
        if (bufLen + count > buf.size) buf = buf.copyOf(max(buf.size * 2, bufLen + count))
        System.arraycopy(mono, 0, buf, bufLen, count)
        bufLen += count
        var start = 0
        while (bufLen - start >= N_FFT) {
            for (i in 0 until N_FFT) { re[i] = (buf[start + i] * window[i]).toDouble(); im[i] = 0.0 }
            var sq = 0.0
            for (i in 0 until HOP) { val s = buf[start + i]; sq += s * s }
            fft()
            val band = FloatArray(N_BANDS)
            val e = edges
            for (b in 0 until N_BANDS) {
                var p = 0.0
                for (k in e[b] until e[b + 1]) {
                    val m = sqrt(re[k] * re[k] + im[k] * im[k]) / (N_FFT / 4.0)
                    p += m * m
                }
                val db = 10.0 * log10(p + 1e-12) + tilt[b]
                band[b] = ((db - DB_FLOOR) / DB_SPAN).toFloat().coerceIn(0f, 1f)
            }
            synchronized(lock) { frames.add(band) }
            if (rmsN >= rms.size) rms = rms.copyOf(rms.size * 2)
            rms[rmsN++] = sqrt(sq / HOP).toFloat()
            start += HOP
        }
        if (start > 0) {
            System.arraycopy(buf, start, buf, 0, bufLen - start)
            bufLen -= start
        }
    }

    private fun percentile(values: FloatArray, q: Double): Float {
        if (values.isEmpty()) return 0f
        val s = values.copyOf(); s.sort()
        val pos = q / 100.0 * (s.size - 1)
        val i = pos.toInt(); val f = (pos - i).toFloat()
        return if (i + 1 < s.size) s[i] * (1f - f) + s[i + 1] * f else s[i]
    }

    /** Levels the bands over the whole song and builds the seek-bar envelope. */
    fun finish() {
        val src: ArrayList<FloatArray>
        synchronized(lock) { src = ArrayList(frames) }
        val n = src.size
        if (n == 0) { done = true; return }
        val shift = FloatArray(N_BANDS)
        val col = FloatArray(n)
        for (b in 0 until N_BANDS) {
            for (i in 0 until n) col[i] = src[i][b]
            shift[b] = (0.70f - percentile(col, 92.0)).coerceIn(-0.25f, 0.16f)
        }
        val lev = Array(n) { i -> FloatArray(N_BANDS) { b -> (((src[i][b] + shift[b]) - 0.10f) * 1.18f).coerceIn(0f, 1f).pow(1.35f) } }
        val win = 41
        val pad = win / 2
        val out = Array(n) { FloatArray(N_BANDS) }
        for (b in 0 until N_BANDS) {
            val cs = DoubleArray(n + 2 * pad + 1)
            for (i in 0 until n + 2 * pad) {
                val idx = (i - pad).coerceIn(0, n - 1)
                cs[i + 1] = cs[i] + lev[idx][b]
            }
            for (i in 0 until n) {
                val mean = ((cs[i + win] - cs[i]) / win).toFloat()
                val v = lev[i][b]
                val punch = ((v - mean) * 2.6f + 0.45f).coerceIn(0f, 1f)
                out[i][b] = (0.55f * v + 0.50f * punch * sqrt(v)).coerceIn(0f, 1f)
            }
        }
        val rn = rmsN
        var sq = 0.0; var cnt = 0
        for (i in 0 until rn) { if (rms[i] > 0.01f) { sq += rms[i].toDouble() * rms[i]; cnt++ } }
        if (cnt > 20) loudnessDb = (10.0 * log10(sq / cnt + 1e-12)).toFloat()
        var env: FloatArray? = null
        if (rn > 0) {
            val bins = min(ENV_BINS, rn)
            val e = FloatArray(bins)
            for (k in 0 until bins) {
                val a = (k.toLong() * rn / bins).toInt()
                val c = ((k + 1).toLong() * rn / bins).toInt()
                var m = 0f
                for (i in a until max(c, a + 1).coerceAtMost(rn)) m = max(m, rms[i])
                e[k] = m
            }
            val ref = percentile(e, 98.0).let { if (it <= 0f) 1f else it }
            for (k in 0 until bins) e[k] = (e[k] / ref).coerceIn(0f, 1f).pow(0.7f)
            env = e
        }
        synchronized(lock) { frames = ArrayList(out.asList()) }
        envelope = env
        done = true
    }

    /** Band levels at a playback position (ms), or null when this part is not analysed yet. */
    fun frameAt(posMs: Double): FloatArray? {
        synchronized(lock) {
            val n = frames.size
            if (n == 0) return null
            val x = (posMs / 1000.0 * sr - N_FFT / 2.0) / HOP
            val row = FloatArray(N_BANDS)
            if (x <= 0) {
                System.arraycopy(frames[0], 0, row, 0, N_BANDS)
            } else {
                val i = x.toInt()
                if (i >= n - 1) {
                    if (i >= n && !done) return null
                    System.arraycopy(frames[n - 1], 0, row, 0, N_BANDS)
                } else {
                    val fr = (x - i).toFloat()
                    val a = frames[i]; val b = frames[i + 1]
                    for (k in 0 until N_BANDS) row[k] = a[k] * (1f - fr) + b[k] * fr
                }
            }
            if (!done) provisional(row, n)
            return row
        }
    }

    /** While the song is still being decoded, level the bands with what was heard so far. */
    private fun provisional(row: FloatArray, n: Int) {
        if (n >= 40 && (prov == null || n - provN >= 120)) {
            val p = FloatArray(N_BANDS)
            val col = FloatArray(n)
            for (b in 0 until N_BANDS) {
                for (i in 0 until n) col[i] = frames[i][b]
                p[b] = (0.70f - percentile(col, 92.0)).coerceIn(-0.25f, 0.16f)
            }
            prov = p; provN = n
        }
        val p = prov ?: return
        for (b in 0 until N_BANDS) row[b] = (((row[b] + p[b]) - 0.10f) * 1.18f).coerceIn(0f, 1f).pow(1.35f)
    }
}

/** Decodes a local or downloaded song in the background and fills an [Analysis]. */
object Analyzer {
    fun start(ctx: Context, source: String): Analysis {
        val an = Analysis(44100)
        val t = Thread({ run(ctx.applicationContext, source, an) }, "nilkon-analysis")
        t.priority = Thread.MIN_PRIORITY + 1
        t.isDaemon = true
        t.start()
        return an
    }

    private fun run(ctx: Context, source: String, an: Analysis) {
        var ex: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            ex = MediaExtractor()
            if (source.startsWith("content:") || source.startsWith("file:")) ex.setDataSource(ctx, Uri.parse(source), null)
            else ex.setDataSource(source)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) { track = i; fmt = f; break }
            }
            if (track < 0 || fmt == null) { an.failed = true; an.finish(); return }
            ex.selectTrack(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            an.setSampleRate(if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100)
            var channels = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            var pcmFloat = false
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(fmt, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            var mono = FloatArray(16384)
            while (!outDone) {
                if (an.cancelled) break
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val ib = codec.getInputBuffer(ii)
                        val n = if (ib != null) ex.readSampleData(ib, 0) else -1
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                            ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10000)
                if (oi >= 0) {
                    val ob = codec.getOutputBuffer(oi)
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        ob.order(ByteOrder.nativeOrder())
                        val ch = max(1, channels)
                        val frames: Int
                        if (pcmFloat) {
                            val fb = ob.asFloatBuffer()
                            frames = fb.remaining() / ch
                            if (mono.size < frames) mono = FloatArray(frames * 2)
                            for (f in 0 until frames) {
                                var s = 0f
                                for (c in 0 until ch) s += fb.get()
                                mono[f] = s / ch
                            }
                        } else {
                            val sb = ob.asShortBuffer()
                            frames = sb.remaining() / ch
                            if (mono.size < frames) mono = FloatArray(frames * 2)
                            for (f in 0 until frames) {
                                var s = 0
                                for (c in 0 until ch) s += sb.get().toInt()
                                mono[f] = s / (32768f * ch)
                            }
                        }
                        an.feed(mono, frames)
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) an.setSampleRate(of.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                    if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmFloat = of.getInteger(MediaFormat.KEY_PCM_ENCODING) == 4
                }
            }
            an.finish()
        } catch (e: Throwable) {
            an.failed = true
            runCatching { an.finish() }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex?.release() }
        }
    }

    @Suppress("unused")
    private fun unusedAbs(x: Int) = abs(x)
}
