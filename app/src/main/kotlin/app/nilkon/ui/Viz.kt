package app.nilkon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------------------
// Vinyl record, turntable, tonearm
// ---------------------------------------------------------------------------------------------------------

fun solveArmAngle(px: Float, py: Float, length: Float, cx: Float, cy: Float, target: Float): Float {
    var best = 0f; var bestErr = 1e9f
    var a = -20f
    while (a <= 70f) {
        val rad = Math.toRadians(a.toDouble())
        val tx = px - length * sin(rad).toFloat()
        val ty = py + length * cos(rad).toFloat()
        val err = abs(hypot(tx - cx, ty - cy) - target)
        if (err < bestErr) { best = a; bestErr = err }
        a += 0.25f
    }
    return best
}

private fun circlePath(c: Offset, r: Float): Path { val p = Path(); p.addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r)); return p }

fun DrawScope.paintVinyl(c: Offset, r: Float, angle: Float, cover: ImageBitmap?, glow: Float, detail: Float) {
    if (glow > 0.001f) {
        drawCircle(Brush.radialGradient(0.70f to T.c1.a(0.30f * glow), 1f to T.c1.a(0f), center = c, radius = r * 1.35f),
            radius = r * 1.35f, center = c)
    }
    drawCircle(Brush.radialGradient(0f to rgb(30, 31, 40), 0.45f to rgb(14, 15, 21), 1f to rgb(4, 4, 8), center = c, radius = r),
        radius = r, center = c)
    val labelR = r * 0.37f
    val ring = Path()
    ring.fillType = PathFillType.EvenOdd
    ring.addOval(androidx.compose.ui.geometry.Rect(c.x - r * 0.985f, c.y - r * 0.985f, c.x + r * 0.985f, c.y + r * 0.985f))
    ring.addOval(androidx.compose.ui.geometry.Rect(c.x - labelR, c.y - labelR, c.x + labelR, c.y + labelR))
    // grooves
    val step = max(0.022f, 0.026f / max(0.3f, detail))
    var k = 0
    var rr = labelR + r * 0.05f
    while (rr < r * 0.965f) {
        drawCircle(Color(1f, 1f, 1f, (7 + (k % 3) * 3) / 255f), radius = rr, center = c, style = Stroke(0.7f))
        rr += r * step
        k++
    }
    for (frac in floatArrayOf(0.52f, 0.69f, 0.83f)) {
        drawCircle(Color(0f, 0f, 0f, 150f / 255f), radius = r * frac, center = c, style = Stroke(max(1f, r * 0.012f)))
    }
    // rotating texture
    val texStops = arrayOf(0.00f to 0, 0.05f to 26, 0.11f to 0, 0.30f to 0, 0.36f to 15, 0.42f to 0,
        0.61f to 0, 0.67f to 26, 0.74f to 0, 0.90f to 0, 0.95f to 12, 1.0f to 0)
    val tex = Brush.sweepGradient(*Array(texStops.size) { i ->
        val e = texStops[texStops.size - 1 - i]
        (1f - e.first) to Color(1f, 1f, 1f, e.second / 255f)
    }, center = c)
    rotate(angle, pivot = c) { drawPath(ring, tex) }
    // fixed sheen
    val sheenStops = arrayOf(0.0f to 0, 0.025f to 70, 0.08f to 0, 0.48f to 0, 0.525f to 62, 0.58f to 0, 1.0f to 0)
    val sheen = Brush.sweepGradient(*Array(sheenStops.size) { i ->
        val e = sheenStops[sheenStops.size - 1 - i]
        (1f - e.first) to Color(1f, 1f, 1f, e.second / 255f)
    }, center = c)
    rotate(-38f, pivot = c) { drawPath(ring, sheen) }
    drawCircle(Color(1f, 1f, 1f, 48f / 255f), radius = r, center = c, style = Stroke(max(1f, r * 0.012f)))
    // label
    rotate(angle, pivot = c) {
        clipPath(circlePath(c, labelR)) {
            if (cover != null) {
                val s = min(cover.width, cover.height)
                drawImage(cover, srcOffset = androidx.compose.ui.unit.IntOffset((cover.width - s) / 2, (cover.height - s) / 2),
                    srcSize = IntSize(s, s), dstOffset = androidx.compose.ui.unit.IntOffset((c.x - labelR).roundToInt(), (c.y - labelR).roundToInt()),
                    dstSize = IntSize((labelR * 2).roundToInt(), (labelR * 2).roundToInt()))
                drawCircle(Brush.radialGradient(0.6f to Color.Transparent, 1f to Color(0f, 0f, 0f, 70f / 255f), center = c, radius = labelR),
                    radius = labelR, center = c)
            } else {
                drawCircle(Brush.sweepGradient(0f to T.c1, 0.33f to T.c3, 0.66f to T.c2, 1f to T.c1, center = c), radius = labelR, center = c)
                drawCircle(Brush.radialGradient(0f to Color(1f, 1f, 1f, 90f / 255f), 1f to Color(1f, 1f, 1f, 0f),
                    center = Offset(c.x, c.y - labelR * 0.35f), radius = labelR * 1.1f), radius = labelR, center = c)
                if (labelR > 14f) {
                    for (i in 0 until 3) {
                        val a = Math.toRadians((i * 120 + 90).toDouble())
                        drawLine(Color(1f, 1f, 1f, 120f / 255f),
                            Offset(c.x + cos(a).toFloat() * labelR * 0.50f, c.y + sin(a).toFloat() * labelR * 0.50f),
                            Offset(c.x + cos(a).toFloat() * labelR * 0.78f, c.y + sin(a).toFloat() * labelR * 0.78f),
                            strokeWidth = max(1f, labelR * 0.05f), cap = StrokeCap.Round)
                    }
                }
            }
        }
        drawCircle(Color(0f, 0f, 0f, 140f / 255f), radius = labelR, center = c, style = Stroke(max(1f, r * 0.01f)))
        drawCircle(Color(1f, 1f, 1f, 60f / 255f), radius = labelR * 0.96f, center = c, style = Stroke(1f))
    }
    // spindle
    drawCircle(Brush.radialGradient(0f to rgb(250, 250, 255), 1f to rgb(150, 156, 175),
        center = Offset(c.x - r * 0.01f, c.y - r * 0.01f), radius = r * 0.06f), radius = r * 0.05f, center = c)
    drawCircle(rgb(12, 12, 18), radius = r * 0.018f, center = c)
}

@Composable
fun Turntable(modifier: Modifier, cover: ImageBitmap?) {
    Canvas(modifier) {
        V.frame
        val s = min(size.width, size.height)
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        val c = Offset(ox + 0.41f * s, oy + 0.55f * s)
        val r = 0.355f * s
        // platter
        drawCircle(Brush.radialGradient(0f to ink(0.07f), 0.92f to ink(0.04f), 1f to ink(0.13f), center = c, radius = r * 1.12f),
            radius = r * 1.1f, center = c)
        drawCircle(ink(0.22f), radius = r * 1.1f, center = c, style = Stroke(1f))
        drawCircle(ink(0.08f), radius = r * 1.045f, center = c, style = Stroke(1f))
        paintVinyl(c, r, V.angle, cover, 0.5f * (0.4f + 0.6f * V.playT), 1f)
        paintArm(ox, oy, s, c, r)
    }
}

private fun DrawScope.paintArm(ox: Float, oy: Float, s: Float, c: Offset, r: Float) {
    val pivX = ox + 0.89f * s; val pivY = oy + 0.10f * s
    val length = 0.60f * s
    val out = solveArmAngle(pivX, pivY, length, c.x, c.y, r * 0.94f)
    val inn = solveArmAngle(pivX, pivY, length, c.x, c.y, r * 0.42f)
    val rest = out - 17f
    val k = smoothstep(V.armT)
    val ang = rest + (out - rest) * k + (inn - out) * V.progress() * k
    val rad = Math.toRadians(ang.toDouble())
    val ux = -sin(rad).toFloat(); val uy = cos(rad).toFloat()
    val pv = Offset(pivX, pivY)
    val tip = Offset(pivX + ux * length, pivY + uy * length)
    val tail = Offset(pivX - ux * 0.11f * s, pivY - uy * 0.11f * s)
    val lift = 1f - k
    val sh = Offset(0.010f * s + 0.02f * s * lift, 0.014f * s + 0.03f * s * lift)
    drawLine(Color(0f, 0f, 0f, (70f - 25f * lift) / 255f), tail + sh, tip + sh, strokeWidth = 0.026f * s, cap = StrokeCap.Round)
    drawLine(rgb(120, 126, 150), tail, pv, strokeWidth = 0.040f * s, cap = StrokeCap.Round)
    drawLine(rgb(30, 32, 44), Offset(tail.x + ux * 0.02f * s, tail.y + uy * 0.02f * s),
        Offset(tail.x + ux * 0.05f * s, tail.y + uy * 0.05f * s), strokeWidth = 0.040f * s, cap = StrokeCap.Butt)
    drawLine(Brush.linearGradient(0f to rgb(238, 241, 252), 1f to rgb(160, 168, 192), start = pv, end = tip),
        pv, tip, strokeWidth = 0.024f * s, cap = StrokeCap.Round)
    drawLine(white(0.55f), Offset(pv.x - uy * 0.006f * s, pv.y + ux * 0.006f * s),
        Offset(tip.x - uy * 0.006f * s, tip.y + ux * 0.006f * s), strokeWidth = 0.007f * s, cap = StrokeCap.Round)
    // headshell
    rotate(ang + 14f, pivot = tip) {
        val l = tip.x - 0.019f * s; val t = tip.y - 0.012f * s
        drawRoundRect(Brush.linearGradient(0f to rgb(245, 247, 255), 1f to rgb(130, 138, 165),
            start = Offset(l, t), end = Offset(l + 0.038f * s, t + 0.062f * s)),
            Offset(l, t), Size(0.038f * s, 0.062f * s), CornerRadius(0.008f * s))
        drawRoundRect(white(120f / 255f), Offset(l, t), Size(0.038f * s, 0.062f * s), CornerRadius(0.008f * s), style = Stroke(1f))
        drawCircle(T.c1.a(0.55f + 0.45f * k), radius = 0.008f * s, center = Offset(tip.x, tip.y + 0.046f * s))
    }
    // pivot base
    drawCircle(Brush.radialGradient(0f to rgb(250, 251, 255), 1f to rgb(110, 118, 145),
        center = Offset(pv.x - 0.01f * s, pv.y - 0.01f * s), radius = 0.07f * s), radius = 0.048f * s, center = pv)
    drawCircle(white(0.5f), radius = 0.048f * s, center = pv, style = Stroke(1f))
    drawCircle(rgb(28, 30, 42), radius = 0.020f * s, center = pv)
    drawCircle(T.c1, radius = 0.008f * s, center = pv)
}

@Composable
fun MiniDisc(size: Dp, cover: ImageBitmap?) {
    Canvas(Modifier.size(size)) {
        V.frame
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = min(this.size.width, this.size.height) / 2f - 4f
        paintVinyl(c, r, V.angle, cover, 0.5f * V.playT, 0.5f)
    }
}

// ---------------------------------------------------------------------------------------------------------
// Spectrum bars, flowing waves, mini bars
// ---------------------------------------------------------------------------------------------------------

@Composable
fun SpectrumView(modifier: Modifier, bars: Int = 30, reflection: Boolean = true) {
    Canvas(modifier) {
        V.frame
        val w = size.width; val h = size.height
        val n = bars
        val gap = 3f * density
        val bw = max(2f, (w - gap * (n - 1)) / n)
        val baseY = h * (if (reflection) 0.74f else 0.96f)
        val maxH = baseY - 2f
        val up = Brush.linearGradient(0f to T.c2, 0.65f to T.c1, 1f to T.c3, start = Offset(0f, baseY), end = Offset(0f, baseY - maxH))
        val refl = Brush.linearGradient(0f to T.c2.a(0.30f), 1f to T.c2.a(0f), start = Offset(0f, baseY), end = Offset(0f, h))
        for (i in 0 until n) {
            val f = i / (n - 1f)
            val v = V.levelAt(V.levels, f)
            val pk = V.levelAt(V.peaks, f)
            val bh = max(2f, v * maxH)
            val x = i * (bw + gap)
            drawRoundRect(up, Offset(x, baseY - bh), Size(bw, bh), CornerRadius(bw / 2f))
            if (pk > 0.04f) drawRoundRect(ink(0.55f * V.playT + 0.1f), Offset(x, baseY - pk * maxH - 4f), Size(bw, 2.2f), CornerRadius(1.1f))
            if (reflection) {
                val rh = min(h - baseY - 1f, bh * 0.45f)
                drawRoundRect(refl, Offset(x, baseY + 2f), Size(bw, max(1f, rh)), CornerRadius(bw / 2f))
            }
        }
    }
}

private fun DrawScope.paintBars(mirror: Boolean) {
    val w = size.width; val h = size.height
    val n = max(16, (w / (9f * density)).toInt())
    val gap = 3f * density
    val bw = max(2f, (w - gap * (n - 1)) / n)
    val brush = Brush.linearGradient(0f to T.c1, 0.5f to T.c2, 1f to T.c3, start = Offset(0f, 0f), end = Offset(w, 0f))
    for (i in 0 until n) {
        val f = i / (n - 1f)
        val v = V.levelAt(V.levels, f)
        val win = 0.35f + 0.65f * sin(PI.toFloat() * f).pow(0.6f)
        if (mirror) {
            val amp = max(2f, v * win * h * 0.46f)
            drawRoundRect(brush, Offset(i * (bw + gap), h / 2f - amp), Size(bw, amp * 2f), CornerRadius(bw / 2f))
        } else {
            val amp = max(2.5f, v * win * h * 0.92f)
            drawRoundRect(brush, Offset(i * (bw + gap), h - amp - 2f), Size(bw, amp), CornerRadius(bw / 2f))
        }
    }
}

@Composable
fun WaveField(modifier: Modifier, style: String = "waves") {
    Canvas(modifier) {
        V.frame
        val w = size.width; val h = size.height
        if (w < 10f) return@Canvas
        if (style != "waves") { paintBars(style == "mirror"); return@Canvas }
        val mid = h / 2f
        val brush = Brush.linearGradient(0f to T.c1.a(0f), 0.18f to T.c1, 0.5f to T.c2, 0.82f to T.c3, 1f to T.c3.a(0f),
            start = Offset(0f, 0f), end = Offset(w, 0f))
        val step = 5f * density
        val count = (w / step).toInt() + 1
        val hist = V.hist
        val t = V.t
        val layers = arrayOf(floatArrayOf(0f, 0.95f, 2.4f, 1.0f), floatArrayOf(3f, 0.55f, 1.7f, 0.80f),
            floatArrayOf(6f, 0.34f, 1.3f, 0.62f), floatArrayOf(10f, 0.22f, 1.0f, 0.46f))
        for (li in layers.indices) {
            val lag = layers[li][0].toInt(); val a = layers[li][1]; val lw = layers[li][2] * density; val scale = layers[li][3]
            val frame = if (hist.size > lag) hist[hist.size - 1 - lag] else V.levels
            val amps = FloatArray(count)
            for (i in 0 until count) {
                val x = min(w, i * step)
                val f = x / w
                val d = abs(2f * f - 1f)
                val lv = V.levelAt(frame, d)
                val window = sin(PI.toFloat() * f).pow(0.75f)
                val base = lv.pow(0.9f) * scale * (h * 0.46f) * window
                val ripple = sin(f * 36f + t * 2.4f + li * 1.7f) * 0.07f * base
                amps[i] = if (V.playT > 0.01f) max(0.8f, base + ripple) else 0.8f
            }
            val sm = FloatArray(count)
            sm[0] = amps[0]; sm[count - 1] = amps[count - 1]
            for (i in 1 until count - 1) sm[i] = (amps[i - 1] + 2f * amps[i] + amps[i + 1]) * 0.25f
            val top = Path(); val bottom = Path()
            for (i in 0 until count) {
                val x = min(w, i * step)
                if (i == 0) { top.moveTo(x, mid - sm[i]); bottom.moveTo(x, mid + sm[i]) }
                else { top.lineTo(x, mid - sm[i]); bottom.lineTo(x, mid + sm[i]) }
            }
            drawPath(top, brush, alpha = a * 0.25f, style = Stroke(lw * 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(top, brush, alpha = a, style = Stroke(lw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(bottom, brush, alpha = a * 0.25f, style = Stroke(lw * 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(bottom, brush, alpha = a * 0.8f, style = Stroke(lw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (li == 0) {
                val fill = Path()
                for (i in 0 until count) { val x = min(w, i * step); if (i == 0) fill.moveTo(x, mid - sm[i]) else fill.lineTo(x, mid - sm[i]) }
                for (i in count - 1 downTo 0) fill.lineTo(min(w, i * step), mid + sm[i])
                fill.close()
                val fg = Brush.linearGradient(0f to T.c1.a(0f), 0.3f to T.c1.a(0.22f), 0.5f to T.c2.a(0.26f),
                    0.7f to T.c3.a(0.22f), 1f to T.c3.a(0f), start = Offset(0f, 0f), end = Offset(w, 0f))
                drawPath(fill, fg)
            }
        }
    }
}

/** Tiny animated equaliser used in track rows. */
fun DrawScope.drawMiniBars(left: Float, top: Float, w: Float, h: Float, color: Color, active: Boolean, count: Int = 4) {
    val gap = 2f * density
    val bw = (w - gap * (count - 1)) / count
    for (i in 0 until count) {
        var v: Float
        if (active) {
            v = 0.25f + 0.75f * V.levelAt(V.levels, (i + 0.5f) / count * 0.6f + 0.05f)
            v = max(v, 0.25f + 0.2f * (0.5f + 0.5f * sin(V.t * 6f + i * 1.7f)))
        } else {
            v = floatArrayOf(0.35f, 0.6f, 0.45f, 0.3f)[i % 4]
        }
        val bh = max(2f, v * h)
        drawRoundRect(color, Offset(left + i * (bw + gap), top + h - bh), Size(bw, bh), CornerRadius(bw / 2f))
    }
}

// ---------------------------------------------------------------------------------------------------------
// Seek bar (waveform of the song)
// ---------------------------------------------------------------------------------------------------------

private fun fallbackWave(seed: Int): FloatArray {
    val rnd = java.util.Random(seed.toLong() * 7919L + 13L)
    fun uni(a: Double, b: Double): Double = a + (b - a) * rnd.nextDouble()
    val a = uni(1.6, 3.4); val b = uni(4.5, 8.0); val c = uni(11.0, 19.0)
    val p1 = uni(0.0, 6.28); val p2 = uni(0.0, 6.28); val p3 = uni(0.0, 6.28)
    val n = 240
    return FloatArray(n) { i ->
        val f = i / n.toDouble()
        val v = 0.52 + 0.26 * sin(f * 6.283 * a + p1) + 0.16 * sin(f * 6.283 * b + p2) + 0.07 * sin(f * 6.283 * c + p3) + uni(-0.05, 0.05)
        max(0.16, min(1.0, v)).toFloat()
    }
}

@Composable
fun SeekBar(modifier: Modifier, positionMs: Long, durationMs: Long, envelope: FloatArray?, envKey: String,
            onSeekStart: () -> Unit, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val fadeBox = remember(envKey) { floatArrayOf(0f) }
    val fb = remember(V.seed) { fallbackWave(V.seed) }
    Canvas(modifier.pointerInput(durationMs) {
        detectTapGestures(onTap = { o ->
            if (durationMs > 0) { onSeekStart(); onSeek((clamp01((o.x - 4f) / max(1f, size.width - 8f)) * durationMs).toLong()) }
        })
    }.pointerInput(durationMs) {
        detectDragGestures(
            onDragStart = { o -> if (durationMs > 0) { drag = true; onSeekStart(); dragValue = clamp01((o.x - 4f) / max(1f, size.width - 8f)) } },
            onDragEnd = { if (drag) { drag = false; onSeek((dragValue * durationMs).toLong()) } },
            onDragCancel = { drag = false },
        ) { change, _ -> if (drag) dragValue = clamp01((change.position.x - 4f) / max(1f, size.width - 8f)) }
    }) {
        V.frame
        if (envelope != null && fadeBox[0] < 1f) fadeBox[0] = min(1f, fadeBox[0] + 0.05f)
        val w = size.width; val h = size.height
        val frac = if (drag) dragValue else if (durationMs > 0) clamp01(positionMs.toFloat() / durationMs) else 0f
        val env = if (envelope != null && envelope.size >= 8) envelope else null
        val px = 4f + (w - 8f) * frac
        val step = 5f * density
        val n = max(8, ((w - 8f) / step).toInt())
        val bw = 3f * density
        val maxh = h - 6f
        val fd = if (env != null) fadeBox[0] else 0f
        val pulse = V.bass * V.playT
        val breathe = V.t * 2.2f
        val played = Brush.linearGradient(0f to T.c1, 1f to T.c2, start = Offset(4f, 0f), end = Offset(w - 4f, 0f))
        val idle = ink(0.24f)
        for (i in 0 until n) {
            val f = i / n.toFloat()
            var v = fb[min(fb.size - 1, (f * fb.size).toInt())]
            if (env != null) v = v * (1f - fd) + env[min(env.size - 1, (f * env.size).toInt())] * fd
            else v *= 0.55f + 0.45f * (0.5f + 0.5f * sin(breathe + i * 0.35f)) * V.playT + 0.45f * (1f - V.playT)
            var bh = max(3f, 4f + v * (maxh - 4f))
            val x = 4f + i * step
            val near = abs((x + bw / 2f) - px)
            if (near < 18f * density) bh += (1f - near / (18f * density)) * (3f + 6f * pulse) * density
            bh = min(bh, maxh)
            if (x + bw / 2f <= px) drawRoundRect(played, Offset(x, h / 2f - bh / 2f), Size(bw, bh), CornerRadius(1.5f * density))
            else drawRoundRect(idle, Offset(x, h / 2f - bh / 2f), Size(bw, bh), CornerRadius(1.5f * density))
        }
        val cy = h / 2f
        drawCircle(Brush.radialGradient(0.3f to T.c1.a(0.55f), 1f to T.c1.a(0f), center = Offset(px, cy), radius = 13f * density),
            radius = 13f * density, center = Offset(px, cy))
        drawCircle(Color.White, radius = (if (drag) 5.5f else 4.5f) * density, center = Offset(px, cy))
    }
}

// ---------------------------------------------------------------------------------------------------------
// Equalizer response curve (drag the nodes)
// ---------------------------------------------------------------------------------------------------------

@Composable
fun EqCurve(modifier: Modifier, values: List<Int>, enabled: Boolean, onBand: (Int, Int) -> Unit) {
    var dragIdx by remember { mutableIntStateOf(-1) }
    val range = 20
    Canvas(modifier.pointerInput(values.size) {
        detectDragGestures(
            onDragStart = { o -> dragIdx = (o.x / (size.width / values.size)).toInt().coerceIn(0, values.size - 1)
                val half = size.height / 2f - 18f * density
                onBand(dragIdx, (((size.height / 2f - o.y) / half) * range).roundToInt().coerceIn(-range, range)) },
            onDragEnd = { dragIdx = -1 },
            onDragCancel = { dragIdx = -1 },
        ) { change, _ ->
            if (dragIdx >= 0) {
                val half = size.height / 2f - 18f * density
                onBand(dragIdx, (((size.height / 2f - change.position.y) / half) * range).roundToInt().coerceIn(-range, range))
            }
        }
    }) {
        V.frame
        val w = size.width; val h = size.height
        val colW = w / values.size
        val half = h / 2f - 18f * density
        fun yOf(v: Int): Float = h / 2f - (v / range.toFloat()) * half
        drawRoundRect(ink(0.05f), Offset(0.5f, 0.5f), Size(w - 1f, h - 1f), CornerRadius(16f * density))
        clipPath(Path().also { it.addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, CornerRadius(16f * density))) }) {
            for (db in intArrayOf(-20, -10, 0, 10, 20)) {
                val y = yOf(db)
                drawLine(ink(if (db == 0) 0.18f else 0.07f), Offset(0f, y), Offset(w, y), strokeWidth = 1f,
                    pathEffect = if (db != 0) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null)
            }
            val n = 48
            val bw = w / n
            val sp = Brush.linearGradient(0f to T.c2.a(0f), 1f to T.c1.a(0.30f), start = Offset(0f, h), end = Offset(0f, 0f))
            for (i in 0 until n) {
                val v = V.levelAt(V.levels, i / (n - 1f))
                val bh = v * h * 0.85f
                drawRoundRect(sp, Offset(i * bw + 1.5f, h - bh), Size(bw - 3f, bh), CornerRadius(2f))
            }
            val pts = Array(values.size) { Offset(colW * (it + 0.5f), yOf(values[it])) }
            val path = Path()
            path.moveTo(0f, pts[0].y)
            path.lineTo(pts[0].x, pts[0].y)
            val ext = ArrayList<Offset>(); ext.add(pts[0]); ext.addAll(pts); ext.add(pts[pts.size - 1])
            for (i in 1 until ext.size - 2) {
                val p0 = ext[i - 1]; val p1 = ext[i]; val p2 = ext[i + 1]; val p3 = ext[i + 2]
                path.cubicTo(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f, p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f, p2.x, p2.y)
            }
            path.lineTo(w, pts[pts.size - 1].y)
            val mid = h / 2f
            val fillP = Path(); fillP.addPath(path)
            fillP.lineTo(w, mid); fillP.lineTo(0f, mid); fillP.close()
            val al = if (enabled) 1f else 0.35f
            drawPath(fillP, Brush.linearGradient(0f to T.c1.a(0.34f), 0.5f to T.c2.a(0.04f), 1f to T.c3.a(0.28f), start = Offset(0f, 0f), end = Offset(0f, h)), alpha = al)
            drawPath(path, T.c2.a(0.28f), alpha = al, style = Stroke(7f * density, cap = StrokeCap.Round))
            drawPath(path, Brush.linearGradient(0f to T.c1, 0.5f to T.c2, 1f to T.c3, start = Offset(0f, 0f), end = Offset(w, 0f)),
                alpha = al, style = Stroke(2.6f * density, cap = StrokeCap.Round))
            for (i in pts.indices) {
                val hot = i == dragIdx
                drawCircle(T.c1.a(if (hot) 0.30f else 0.14f), radius = (if (hot) 11f else 8f) * density, center = pts[i], alpha = al)
                drawCircle(Color.White, radius = 4.6f * density, center = pts[i], alpha = al)
                drawCircle(T.c2, radius = 2.2f * density, center = pts[i], alpha = al)
            }
        }
        drawRoundRect(ink(0.16f), Offset(0.5f, 0.5f), Size(w - 1f, h - 1f), CornerRadius(16f * density), style = Stroke(1f))
    }
}

@Suppress("unused")
private fun unusedViz(m: Modifier) = m.fillMaxSize()
