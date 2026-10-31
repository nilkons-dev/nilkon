package app.nilkon.ui

import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.caverock.androidsvg.SVG

/** Draws an SVG document (parsed from text) into the given box. Parse errors draw nothing. */
fun DrawScope.drawSvgText(text: String, left: Float, top: Float, w: Float, h: Float) {
    val svg: SVG = try { SVG.getFromString(text) } catch (e: Exception) { return }
    drawIntoCanvas { c -> svg.renderToCanvas(c.nativeCanvas, RectF(left, top, left + w, top + h)) }
}

private val iconCache = HashMap<String, SVG>()

private fun iconSvg(name: String, color: Color): SVG? {
    val a = (color.alpha * 255f + 0.5f).toInt()
    val key = name + "|" + hexColor(color) + "|" + a
    iconCache[key]?.let { return it }
    val def = ICON_SET[name] ?: return null
    val hexc = hexColor(color)
    val al = fmt(color.alpha)
    val g = if (def.first == "stroke")
        "fill=\"none\" stroke=\"$hexc\" stroke-opacity=\"$al\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\""
    else
        "fill=\"$hexc\" fill-opacity=\"$al\" stroke=\"$hexc\" stroke-opacity=\"$al\" stroke-width=\"1.6\" stroke-linejoin=\"round\""
    val text = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\"><g $g>${def.second}</g></svg>"
    val svg = try { SVG.getFromString(text) } catch (e: Exception) { return null }
    if (iconCache.size > 600) iconCache.clear()
    iconCache[key] = svg
    return svg
}

/** Draws icon [name] into the square whose top-left corner is (x, y) and whose side is [size] pixels. */
fun DrawScope.drawIcon(name: String, x: Float, y: Float, size: Float, color: Color) {
    val svg = iconSvg(name, color) ?: return
    drawIntoCanvas { c -> svg.renderToCanvas(c.nativeCanvas, RectF(x, y, x + size, y + size)) }
}

fun DrawScope.drawIconCentered(name: String, cx: Float, cy: Float, size: Float, color: Color) =
    drawIcon(name, cx - size / 2f, cy - size / 2f, size, color)

@Suppress("unused")
private fun unusedRect(r: Rect) = r
