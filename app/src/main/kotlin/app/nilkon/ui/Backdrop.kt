package app.nilkon.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.nilkon.R
import app.nilkon.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Built-in backgrounds (same pictures as the desktop player). */
val BACKGROUNDS = listOf("aurora" to "Aurora", "midnight" to "Midnight", "deepspace" to "Deep Space",
    "bluehaze" to "Blue Haze", "superdark" to "Super Dark")

private fun drawableFor(key: String): Int? = when (key) {
    "midnight" -> R.drawable.bg_midnight
    "deepspace" -> R.drawable.bg_deepspace
    "bluehaze" -> R.drawable.bg_bluehaze
    else -> null
}

private fun decode(ctx: android.content.Context, key: String, maxSide: Int): Bitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    fun open(o: BitmapFactory.Options): Bitmap? {
        val res = drawableFor(key)
        return if (key == "custom") {
            val f = File(Store.str("custom_bg")); if (f.isFile) BitmapFactory.decodeFile(f.path, o) else null
        } else if (res != null) BitmapFactory.decodeResource(ctx.resources, res, o) else null
    }
    open(opts)
    if (opts.outWidth <= 0) return@runCatching null
    var sample = 1
    while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val real = BitmapFactory.Options().apply { inSampleSize = sample }
    open(real)
}.getOrNull()

/** The window backdrop: flat colour, optional picture (dimmed and/or blurred), or the Super Dark gradient. */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val key = Store.str("background")
    val custom = Store.str("custom_bg")
    val dim = Store.int("bg_dim").coerceIn(0, 90) / 100f
    val blur = Store.int("bg_blur").coerceIn(0, 100)
    val src by produceState<Bitmap?>(null, key, custom) {
        value = if (key == "aurora" || key == "superdark") null
        else withContext(Dispatchers.IO) { decode(ctx, key, 1400) }
    }
    val blurred by produceState<ImageBitmap?>(null, src, blur) {
        val s = src
        value = if (s == null) null else withContext(Dispatchers.Default) {
            if (blur <= 0) s.asImageBitmap() else {
                val f = 1f + blur / 100f * 22f
                val small = Bitmap.createScaledBitmap(s, max(8, (s.width / f).toInt()), max(8, (s.height / f).toInt()), true)
                small.asImageBitmap()
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(pageBg)
        if (key == "superdark") {
            drawRect(Brush.verticalGradient(listOf(rgb(1, 2, 7), rgb(0, 0, 2))))
            drawRect(Brush.radialGradient(listOf(rgb(18, 40, 110, 70f / 255f), rgb(18, 40, 110, 0f)),
                center = Offset(w * 0.5f, h * 1.05f), radius = w * 0.55f))
        } else {
            val img = blurred
            if (img != null) {
                // "cover" crop: fill the whole screen, centred
                val scale = max(w / img.width, h / img.height)
                val sw = min(img.width.toFloat(), w / scale); val sh = min(img.height.toFloat(), h / scale)
                val sx = (img.width - sw) / 2f; val sy = (img.height - sh) / 2f
                drawImage(img, IntOffset(sx.roundToInt(), sy.roundToInt()), IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)),
                    IntOffset(0, 0), IntSize(w.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.Medium)
                drawRect(surface(if (T.dark) dim else dim * 0.7f))
            }
        }
    }
}

/** A small preview of a background for the Appearance page. */
@Composable
fun BackgroundPreview(key: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val custom = Store.str("custom_bg")
    val bmp by produceState<ImageBitmap?>(null, key, custom) {
        value = if (key == "aurora" || key == "superdark") null
        else withContext(Dispatchers.IO) { decode(ctx, key, 320)?.asImageBitmap() }
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (key == "aurora") {
            drawRect(if (T.dark) rgb(8, 10, 15) else rgb(236, 240, 247))
            drawCircle(Brush.radialGradient(listOf(T.c1.a(0.55f), Color.Transparent), Offset(w * 0.22f, h * 0.3f), w * 0.5f), w * 0.5f, Offset(w * 0.22f, h * 0.3f))
            drawCircle(Brush.radialGradient(listOf(T.c2.a(0.45f), Color.Transparent), Offset(w * 0.8f, h * 0.25f), w * 0.45f), w * 0.45f, Offset(w * 0.8f, h * 0.25f))
            drawCircle(Brush.radialGradient(listOf(T.c3.a(0.45f), Color.Transparent), Offset(w * 0.5f, h * 0.95f), w * 0.55f), w * 0.55f, Offset(w * 0.5f, h * 0.95f))
        } else if (key == "superdark") {
            drawRect(Brush.verticalGradient(listOf(rgb(1, 2, 7), rgb(0, 0, 2))))
            drawRect(Brush.radialGradient(listOf(rgb(18, 40, 110, 70f / 255f), rgb(18, 40, 110, 0f)), Offset(w * 0.5f, h * 1.05f), w * 0.55f))
        } else {
            val img = bmp
            if (img == null) drawRect(rgb(20, 26, 40)) else {
                val scale = max(w / img.width, h / img.height)
                val sw = min(img.width.toFloat(), w / scale); val sh = min(img.height.toFloat(), h / scale)
                drawImage(img, IntOffset(((img.width - sw) / 2f).roundToInt(), ((img.height - sh) / 2f).roundToInt()),
                    IntSize(sw.roundToInt().coerceAtLeast(1), sh.roundToInt().coerceAtLeast(1)), IntOffset(0, 0), IntSize(w.roundToInt(), h.roundToInt()))
            }
        }
    }
}
