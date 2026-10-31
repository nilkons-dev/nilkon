package app.nilkon.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.Locale
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI

fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v
fun clampF(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v
fun smoothstep(x: Float): Float { val t = clamp01(x); return t * t * (3f - 2f * t) }

/** Number to text that never depends on the phone's language (Bangla digits would break the SVG art). */
fun fmt(v: Double, digits: Int = 3): String = String.format(Locale.US, "%." + digits + "f", v)
fun fmt(v: Float, digits: Int = 3): String = fmt(v.toDouble(), digits)

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}

fun Color.a(alpha: Float): Color = this.copy(alpha = clamp01(alpha))
fun rgb(r: Int, g: Int, b: Int, a: Float = 1f): Color = Color(r / 255f, g / 255f, b / 255f, a)

fun hsvColor(h: Float, s: Float, v: Float, a: Float = 1f): Color {
    var hh = h % 1f
    if (hh < 0f) hh += 1f
    return Color.hsv(hh * 360f, clamp01(s), clamp01(v), clamp01(a))
}

fun lerpRgb(a: Color, b: Color, t: Float): Color {
    val k = clamp01(t)
    return Color(a.red + (b.red - a.red) * k, a.green + (b.green - a.green) * k,
        a.blue + (b.blue - a.blue) * k, a.alpha + (b.alpha - a.alpha) * k)
}

private fun toHsv(c: Color): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), out)
    return out
}

/** Same meaning as Qt's QColor.lighter(factor): factor 130 = 30% brighter. */
fun lighter(c: Color, factor: Int): Color {
    val h = toHsv(c)
    var v = h[2] * factor / 100f
    var s = h[1]
    if (v > 1f) { s = max(0f, s - (v - 1f)); v = 1f }
    return Color.hsv(h[0], s, v, c.alpha)
}

fun darker(c: Color, factor: Int): Color {
    val h = toHsv(c)
    return Color.hsv(h[0], h[1], clamp01(h[2] * 100f / factor), c.alpha)
}

fun hueFor(text: String): Float {
    val crc = CRC32()
    crc.update(text.toByteArray(Charsets.UTF_8))
    return (crc.value % 360L).toFloat() / 360f
}

fun hexColor(c: Color): String {
    return String.format(Locale.US, "#%02x%02x%02x", (c.red * 255f + 0.5f).toInt().coerceIn(0, 255),
        (c.green * 255f + 0.5f).toInt().coerceIn(0, 255), (c.blue * 255f + 0.5f).toInt().coerceIn(0, 255))
}

private fun hx(s: String): Color = Color(android.graphics.Color.parseColor(s))

val ACCENTS: LinkedHashMap<String, Triple<Color, Color, Color>> = linkedMapOf(
    "Cat Orange" to Triple(hx("#FF9468"), hx("#FFB596"), hx("#F59E0B")),
    "Nilkon Blue" to Triple(hx("#3B82F6"), hx("#22D3EE"), hx("#6366F1")),
    "Deep Ocean" to Triple(hx("#2563EB"), hx("#0EA5E9"), hx("#38BDF8")),
    "Midnight Indigo" to Triple(hx("#4F46E5"), hx("#3B82F6"), hx("#8B5CF6")),
    "Aurora" to Triple(hx("#34D399"), hx("#22D3EE"), hx("#818CF8")),
    "Sunset" to Triple(hx("#FB7185"), hx("#F59E0B"), hx("#C084FC")),
    "Neon" to Triple(hx("#F43F5E"), hx("#A855F7"), hx("#22D3EE")),
    "Ice" to Triple(hx("#E0F2FE"), hx("#7DD3FC"), hx("#A5B4FC")),
)
const val DEFAULT_ACCENT = "Cat Orange"

/** Shared palette: c1..c3 are the colours on screen, they ease towards t1..t3 (same as the desktop player). */
object T {
    var dark by mutableStateOf(true)
    var accentName by mutableStateOf(DEFAULT_ACCENT)
    var c1 by mutableStateOf(ACCENTS.getValue(DEFAULT_ACCENT).first)
    var c2 by mutableStateOf(ACCENTS.getValue(DEFAULT_ACCENT).second)
    var c3 by mutableStateOf(ACCENTS.getValue(DEFAULT_ACCENT).third)
    private var t1 = c1
    private var t2 = c2
    private var t3 = c3

    fun setAccent(name: String, snap: Boolean = false) {
        val cols = ACCENTS[name] ?: ACCENTS.getValue(DEFAULT_ACCENT)
        accentName = if (ACCENTS.containsKey(name)) name else DEFAULT_ACCENT
        setTargets(cols.first, cols.second, cols.third, snap)
    }

    fun setHueTargets(h: Float, snap: Boolean = false) {
        setTargets(hsvColor(h, 0.62f, 1f), hsvColor(h + 0.14f, 0.60f, 1f), hsvColor(h + 0.34f, 0.60f, 1f), snap)
    }

    fun setTargets(a: Color, b: Color, c: Color, snap: Boolean = false) {
        t1 = a; t2 = b; t3 = c
        if (snap) { c1 = a; c2 = b; c3 = c }
    }

    private fun near(a: Color, b: Color): Boolean =
        (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)) * 255f < 4f

    fun settled(): Boolean = near(c1, t1) && near(c2, t2) && near(c3, t3)

    fun step(k: Float) {
        if (settled()) return
        c1 = lerpRgb(c1, t1, k); c2 = lerpRgb(c2, t2, k); c3 = lerpRgb(c3, t3, k)
        if (settled()) { c1 = t1; c2 = t2; c3 = t3 }
    }

    val onAccent: Color
        get() {
            val m = lerpRgb(c1, c2, 0.5f)
            val lum = 0.299f * m.red + 0.587f * m.green + 0.114f * m.blue
            return if (lum > 0.64f) rgb(8, 14, 34) else Color.White
        }

    /** Accent tone used for text on the page (lighter on dark, deeper on light). */
    val accentText: Color get() = if (dark) lighter(c1, 125) else darker(c1, 118)
    val accentTextStrong: Color get() = if (dark) lighter(c1, 130) else darker(c1, 120)
}

fun ink(a: Float = 1f): Color = if (T.dark) Color(1f, 1f, 1f, clamp01(a)) else rgb(12, 20, 44, clamp01(a))
fun surface(a: Float = 1f): Color = if (T.dark) rgb(5, 9, 22, clamp01(a)) else Color(1f, 1f, 1f, clamp01(a))
fun white(a: Float): Color = Color(1f, 1f, 1f, clamp01(a))

val pageBg: Color get() = if (T.dark) rgb(8, 10, 15) else rgb(236, 240, 247)
val cardFill: Color get() = if (T.dark) rgb(14, 17, 24, 0.925f) else Color(1f, 1f, 1f, 0.93f)
val cardEdge: Color get() = if (T.dark) Color(1f, 1f, 1f, 0.086f) else rgb(15, 23, 42, 0.133f)
val menuBg: Color get() = if (T.dark) rgb(21, 25, 34) else Color.White
val textMain: Color get() = if (T.dark) hx("#f1f5ff") else hx("#0c142c")
val textSub: Color get() = if (T.dark) Color(1f, 1f, 1f, 0.58f) else rgb(12, 20, 44, 0.62f)
val textEyebrow: Color get() = if (T.dark) Color(1f, 1f, 1f, 0.46f) else rgb(12, 20, 44, 0.50f)
val hairline: Color get() = if (T.dark) Color(1f, 1f, 1f, 0.16f) else rgb(12, 20, 44, 0.18f)

fun dominantHue(pixels: IntArray, w: Int, h: Int): Float? {
    var sx = 0.0; var sy = 0.0; var total = 0.0
    val hsv = FloatArray(3)
    for (i in 0 until w * h) {
        val px = pixels[i]
        android.graphics.Color.colorToHSV(px, hsv)
        val hh = hsv[0] / 360f
        val wgt = (hsv[1] * hsv[1] * hsv[2]).toDouble() + 0.002
        sx += cos(hh * 2 * PI) * wgt
        sy += sin(hh * 2 * PI) * wgt
        total += wgt
    }
    if (total < 0.5 || (abs(sx) + abs(sy)) / total < 0.05) return null
    var r = (atan2(sy, sx) / (2 * PI)).toFloat() % 1f
    if (r < 0f) r += 1f
    return r
}
