package app.nilkon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.nilkon.R
import app.nilkon.data.Covers
import app.nilkon.data.Song
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------------------

@Composable
fun Txt(text: String, size: Int = 13, weight: FontWeight = FontWeight.Medium, color: Color = textMain, maxLines: Int = 1,
        modifier: Modifier = Modifier, align: TextAlign? = null, spacing: Float = 0f) {
    Text(text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, textAlign = align, letterSpacing = spacing.sp, lineHeight = (size * 1.3f).sp)
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) =
    Txt(text, 10, FontWeight.ExtraBold, textEyebrow, 1, modifier, null, 1.2f)

@Composable
fun SectionTitle(text: String, sub: String = "") {
    Column {
        Txt(text, 22, FontWeight.ExtraBold)
        if (sub.isNotEmpty()) Txt(sub, 12, FontWeight.Normal, textSub, 2)
    }
}

// ---------------------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------------------

@Composable
fun GlassPanel(modifier: Modifier = Modifier, radius: Dp = 14.dp, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.clip(shape).background(cardFill).border(1.dp, cardEdge, shape), content = content)
}

// ---------------------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------------------

/** Custom button, same look as the desktop GButton. kinds: primary, glass, chip, ghost, flat, round. */
@Composable
fun GButton(text: String = "", icon: String? = null, kind: String = "glass", modifier: Modifier = Modifier,
            active: Boolean = false, enabled: Boolean = true, iconSize: Int = 18, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val h by animateFloatAsState(if (pressed) 1f else 0f, tween(170), label = "gb")
    val round = kind == "chip" || kind == "round" || kind == "flat" || kind == "ghost"
    val radius = if (round) 40.dp else 10.dp
    val shape = RoundedCornerShape(radius)
    val sizeMod = when (kind) {
        "round", "flat" -> if (text.isEmpty()) Modifier.size(40.dp) else Modifier.height(40.dp)
        "chip" -> Modifier.height(32.dp)
        else -> Modifier.height(42.dp)
    }
    val fg: Color = when (kind) {
        "primary" -> T.onAccent
        "glass", "round", "chip" -> if (active) (if (T.dark) lighter(T.c1, 125) else darker(T.c1, 120)) else ink(0.92f)
        "flat" -> if (active) (if (T.dark) lighter(T.c1, 120) else darker(T.c1, 115)) else ink(0.62f + 0.34f * h)
        else -> ink(0.70f + 0.25f * h)
    }
    Box(modifier.then(sizeMod).alpha(if (enabled) 1f else 0.4f)
        .graphicsLayer { val s = if (pressed) 0.965f else 1f; scaleX = s; scaleY = s }
        .drawBehind {
            val r = CornerRadius(radius.toPx())
            when (kind) {
                "primary" -> drawRoundRect(lighter(T.c1, 100 + (12 * h).toInt()), cornerRadius = r)
                "glass", "round", "chip" -> {
                    if (active) {
                        drawRoundRect(T.c1.a(0.18f + 0.07f * h), cornerRadius = r)
                        drawRoundRect(T.c1.a(0.65f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                    } else {
                        drawRoundRect(ink(0.05f + 0.06f * h), cornerRadius = r)
                        drawRoundRect(ink(0.13f + 0.10f * h), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                    }
                }
                "flat", "ghost" -> if (h > 0.01f) drawRoundRect(ink(0.10f * h), cornerRadius = r)
            }
            if (kind == "flat" && active) drawCircle(T.c1, 1.8.dp.toPx(), Offset(size.width / 2f, size.height - 2.5.dp.toPx()))
        }
        .clickable(src, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Row(Modifier.padding(horizontal = if (text.isEmpty()) 0.dp else if (kind == "chip") 13.dp else 19.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Canvas(Modifier.size(iconSize.dp)) { drawIcon(icon, 0f, 0f, size.width, fg) }
                if (text.isNotEmpty()) Spacer(Modifier.width(9.dp))
            }
            if (text.isNotEmpty()) Txt(text, if (kind == "chip") 12 else 13, FontWeight.SemiBold, fg)
        }
    }
}

@Composable
fun ToggleSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(180, easing = FastOutSlowInEasing), label = "sw")
    Canvas(Modifier.size(46.dp, 26.dp).alpha(if (enabled) 1f else 0.4f)
        .pointerInput(checked, enabled) { detectTapGestures { if (enabled) onChange(!checked) } }) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(ink(0.14f), Offset(1f, 1f), Size(size.width - 2f, size.height - 2f), r)
        if (t > 0.01f) drawRoundRect(T.c1, Offset(1f, 1f), Size(size.width - 2f, size.height - 2f), r, alpha = t)
        drawRoundRect(ink(0.30f), Offset(1f, 1f), Size(size.width - 2f, size.height - 2f), r,
            style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        val kr = size.height / 2f - 1f - 3.dp.toPx()
        val kx = 1f + size.height / 2f - 1f + (size.width - size.height) * t
        drawCircle(Color(1f, 1f, 1f, 245f / 255f), kr, Offset(kx, size.height / 2f))
    }
}

/** Slider with glowing fill (same look as the desktop GlassSlider). */
@Composable
fun GlassSlider(value: Int, min: Int, max: Int, modifier: Modifier = Modifier, bipolar: Boolean = false, vertical: Boolean = false,
                enabled: Boolean = true, onChange: (Int) -> Unit) {
    var down by remember { mutableStateOf(false) }
    Canvas(modifier.alpha(if (enabled) 1f else 0.4f)
        .pointerInput(enabled, min, max, vertical) {
            fun at(o: Offset): Int {
                val pad = 11.dp.toPx()
                val f = if (vertical) { val a = size.height - pad; val b = pad; (o.y - a) / (b - a) } else (o.x - pad) / (size.width - 2f * pad)
                return (min + clamp01(f) * (max - min)).roundToInt()
            }
            detectTapGestures(onPress = { o -> if (enabled) { down = true; onChange(at(o)); tryAwaitRelease(); down = false } },
                onDoubleTap = { if (enabled && bipolar) onChange(0) })
        }
        .pointerInput(enabled, min, max, vertical) {
            fun at(o: Offset): Int {
                val pad = 11.dp.toPx()
                val f = if (vertical) { val a = size.height - pad; val b = pad; (o.y - a) / (b - a) } else (o.x - pad) / (size.width - 2f * pad)
                return (min + clamp01(f) * (max - min)).roundToInt()
            }
            detectDragGestures(onDragStart = { if (enabled) down = true }, onDragEnd = { down = false }, onDragCancel = { down = false }) { change, _ ->
                if (enabled) onChange(at(change.position))
            }
        }) {
        val pad = 11.dp.toPx()
        val gw = 6.dp.toPx()
        fun pos(v: Int): Float {
            val f = (v - min) / max(1, max - min).toFloat()
            return if (vertical) (size.height - pad) + (pad - (size.height - pad)) * f else pad + (size.width - 2f * pad) * f
        }
        val p = pos(value)
        val origin = pos(if (bipolar) 0 else min)
        val lo = min(p, origin); val hi = max(p, origin)
        val groove: Offset; val gsize: Size; val fillO: Offset; val fillS: Size; val grad: Brush
        if (vertical) {
            groove = Offset(size.width / 2f - gw / 2f, pad); gsize = Size(gw, size.height - 2f * pad)
            fillO = Offset(groove.x, lo); fillS = Size(gw, max(0.5f, hi - lo))
            grad = Brush.linearGradient(0f to T.c1, 1f to T.c2, start = Offset(0f, size.height - pad), end = Offset(0f, pad))
        } else {
            groove = Offset(pad, size.height / 2f - gw / 2f); gsize = Size(size.width - 2f * pad, gw)
            fillO = Offset(lo, groove.y); fillS = Size(max(0.5f, hi - lo), gw)
            grad = Brush.linearGradient(0f to T.c1, 1f to T.c2, start = Offset(pad, 0f), end = Offset(size.width - pad, 0f))
        }
        drawRoundRect(ink(0.14f), groove, gsize, CornerRadius(gw / 2f))
        if (bipolar) {
            if (vertical) drawLine(ink(0.35f), Offset(groove.x - 3.dp.toPx(), origin), Offset(groove.x + gw + 3.dp.toPx(), origin), 1f)
            else drawLine(ink(0.35f), Offset(origin, groove.y - 3.dp.toPx()), Offset(origin, groove.y + gw + 3.dp.toPx()), 1f)
        }
        drawRoundRect(T.c1.a(0.22f), Offset(fillO.x - 1.5f, fillO.y - 1.5f), Size(fillS.width + 3f, fillS.height + 3f), CornerRadius(4.5f))
        drawRoundRect(grad, fillO, fillS, CornerRadius(gw / 2f))
        val kc = if (vertical) Offset(size.width / 2f, p) else Offset(p, size.height / 2f)
        val kr = (if (down) 8f else 7f) * density
        drawCircle(Brush.radialGradient(0.5f to Color(0f, 0f, 0f, 90f / 255f), 1f to Color(0f, 0f, 0f, 0f),
            center = Offset(kc.x, kc.y + 1.5f), radius = kr + 5f), kr + 5f, Offset(kc.x, kc.y + 1.5f))
        drawCircle(Brush.linearGradient(0f to Color.White, 1f to rgb(214, 220, 238), start = Offset(kc.x, kc.y - kr), end = Offset(kc.x, kc.y + kr)), kr, kc)
        drawCircle(T.c1, kr * 0.32f, kc)
    }
}

// ---------------------------------------------------------------------------------------------------------
// Rows used by the settings pages
// ---------------------------------------------------------------------------------------------------------

@Composable
fun SettingRow(title: String, desc: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, 14, FontWeight.SemiBold, textMain, 2)
            if (desc.isNotEmpty()) Txt(desc, 12, FontWeight.Normal, textSub, 4)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

// ---------------------------------------------------------------------------------------------------------
// Cover thumbnails
// ---------------------------------------------------------------------------------------------------------

@Composable
fun Thumb(song: Song?, size: Dp, key: String = song?.key ?: "", iconAlpha: Float = 0.85f, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val img = Covers.peek(song, px)
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val radius = w * 0.24f
        val rr = Path().also { it.addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, w, CornerRadius(radius))) }
        clipPath(rr) {
            if (img != null) {
                val s = min(img.width, img.height)
                drawImage(img, IntOffset((img.width - s) / 2, (img.height - s) / 2), IntSize(s, s), IntOffset(0, 0), IntSize(w.roundToInt(), w.roundToInt()))
            } else {
                val hh = hueFor(key)
                drawRect(Brush.linearGradient(0f to hsvColor(0.58f + 0.08f * ((hh * 7f) % 1f), 0.65f, 0.78f),
                    1f to hsvColor(0.64f + 0.06f * ((hh * 5f) % 1f), 0.78f, 0.38f), start = Offset(0f, 0f), end = Offset(w, w)))
                drawIconCentered("music", w / 2f, w / 2f, w * 0.46f, white(iconAlpha))
            }
        }
        drawRoundRect(if (T.dark) Color(1f, 1f, 1f, 46f / 255f) else rgb(12, 20, 44, 50f / 255f), Offset(0.5f, 0.5f), Size(w - 1f, w - 1f),
            CornerRadius(radius), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
    }
}

// ---------------------------------------------------------------------------------------------------------
// Cat logo (eyes follow the finger, blinks, sleeps after 10 s, happy when tapped)
// ---------------------------------------------------------------------------------------------------------

object Touch {
    @Volatile var x = -1f
    @Volatile var y = -1f
    @Volatile var stamp = 0L
}

private class LogoState {
    var tick by mutableIntStateOf(0)
    var t0 = 0.0
    var last = 0.0
    var lookX = 0f; var lookY = 0f
    var tx = 0f; var ty = 0f
    var sleep = 0f
    var sleeping = false
    var blink: Float? = null
    var tap: Float? = null
    var lastMove = 0.0
    var idleAt = 0.0
    var nextBlink = 0.0
    var second: Double? = null
    var seen = 0L
    var cx = 0f; var cy = 0f; var w = 1f; var h = 1f
    val rnd = java.util.Random()

    fun start(now: Double) { t0 = now; last = now; idleAt = now + 10.0; nextBlink = now + 2.2 + rnd.nextDouble() * 3.0; seen = Touch.stamp }

    fun wake(now: Double) { sleeping = false; blink = 0f; second = now + 0.3 }

    fun update(now: Double) {
        val dt = min(0.1, now - last).toFloat(); last = now
        if (Touch.stamp != seen) {
            seen = Touch.stamp
            val dx = Touch.x - cx
            val dy = Touch.y - (cy + h * 0.07f)
            val d = hypot(dx, dy).let { if (it == 0f) 1f else it }
            val k = min(d / (w * 0.5f), 1f)
            tx = dx / d * k; ty = dy / d * k
            if (sleeping) wake(now) else if (now - lastMove > 0.8) blink = 0f
            lastMove = now
            idleAt = now + 10.0
        }
        if (!sleeping && now >= idleAt) { sleeping = true; tx = 0f; ty = 0f }
        val f = (1.0 - Math.pow(1.0 - 0.14, dt * 60.0)).toFloat()
        val gx = if (sleeping) 0f else tx; val gy = if (sleeping) 0f else ty
        lookX += (gx - lookX) * f; lookY += (gy - lookY) * f
        val g = (1.0 - Math.pow(1.0 - 0.06, dt * 60.0)).toFloat()
        sleep += ((if (sleeping) 1f else 0f) - sleep) * g
        blink?.let { b -> val nb = b + dt / 0.22f; blink = if (nb >= 1f) null else nb }
        second?.let { s -> if (now >= s) { second = null; blink = 0f } }
        if (now >= nextBlink) {
            if (!sleeping) { blink = 0f; if (rnd.nextFloat() < 0.25f) second = now + 0.3 }
            nextBlink = now + 2.2 + rnd.nextDouble() * 3.0
        }
        tap?.let { tp -> val nt = tp + dt; tap = if (nt >= 1.1f) null else nt }
        tick++
    }
}

@Composable
fun CatLogo(size: Dp, modifier: Modifier = Modifier) {
    val st = remember { LogoState() }
    LaunchedEffect(Unit) {
        st.start(System.nanoTime() / 1e9)
        while (true) {
            androidx.compose.runtime.withFrameNanos { ns -> st.update(ns / 1e9) }
        }
    }
    Canvas(modifier.size(size)
        .onGloballyPositioned { c ->
            val p = c.positionInRoot()
            st.cx = p.x + c.size.width / 2f; st.cy = p.y + c.size.height / 2f
            st.w = c.size.width.toFloat(); st.h = c.size.height.toFloat()
        }
        .pointerInput(Unit) {
            detectTapGestures { val now = System.nanoTime() / 1e9; if (st.sleeping) st.wake(now); st.tap = 0f; st.idleAt = now + 10.0 }
        }) {
        st.tick
        val svg = CatArt.logoSvg((st.last - st.t0).toFloat(), st.lookX, st.lookY, st.sleep, st.blink, st.tap, V.reduced)
        drawSvgText(svg, 0f, 0f, this.size.width, this.size.height)
    }
}

// ---------------------------------------------------------------------------------------------------------
// Cat-ear player buttons
// ---------------------------------------------------------------------------------------------------------

private class Tween(var value: Float, val duration: Float, val curve: (Float) -> Float) {
    var target = value
    private var from = value
    private var t = 1f
    fun set(v: Float) { if (v != target) { target = v; from = value; t = 0f } }
    fun step(dt: Float): Boolean {
        if (t >= 1f) return false
        t = min(1f, t + dt / duration)
        value = from + (target - from) * curve(t)
        if (t >= 1f) value = target
        return true
    }
    val moving: Boolean get() = t < 1f
}

private class ButtonState(val kind: String) {
    var tick by mutableIntStateOf(0)
    val hover = Tween(0f, 0.15f) { CatArt.smooth(it) }
    val press = Tween(1f, 0.18f) { CatArt.bezier(.3f, 1.6f, .5f, 1f, it) }
    val paw = Tween(0f, 0.30f) { CatArt.bezier(.3f, 1.9f, .5f, 1f, it) }
    var tap: Float? = null
    var pop: Float? = null
    var last = 0.0
    var wakeCount by mutableIntStateOf(0)
    fun wake() { wakeCount++ }
    /** Returns true while something still moves. */
    fun update(now: Double, glowing: Boolean): Boolean {
        val dt = min(0.1, now - last).toFloat(); last = now
        var busy = false
        for (tw in arrayOf(hover, press, paw)) { val m = tw.step(dt); busy = m || tw.moving || busy }
        tap?.let { tp ->
            val nt = tp + dt
            if (nt >= (if (kind != "toggle") 0.55f else 0.6f)) tap = null else { tap = nt; busy = true }
        }
        pop?.let { pp -> val np = pp + dt / 0.3f; if (np >= 1f) pop = null else { pop = np; busy = true } }
        tick++
        return busy || glowing
    }
}

/** Cat-ear button. kind: toggle (shuffle / repeat), prev, next, play. */
@Composable
fun CatButton(kind: String, icon: String, scale: Float = 1f, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val st = remember(kind) { ButtonState(kind) }
    var shown by remember { mutableStateOf(icon) }
    var badge = ""
    var name = icon
    if (name == "repeat-1") { name = "repeat"; badge = "1" }
    val glowing = kind == "play" && icon == "pause"
    LaunchedEffect(name, badge) {
        if (shown != name) { if (kind == "play") st.pop = 0f; shown = name; st.wake() }
    }
    LaunchedEffect(active) { st.paw.set(if (active) 1f else 0f); st.wake() }
    LaunchedEffect(st.wakeCount, glowing) {
        st.last = System.nanoTime() / 1e9
        var busy = true
        while (busy) {
            androidx.compose.runtime.withFrameNanos { ns -> busy = st.update(ns / 1e9, glowing) }
        }
    }
    val w = (if (kind == "play") CatArt.PLAY_W else CatArt.SMALL_W) * scale
    val h = (if (kind == "play") CatArt.PLAY_H else CatArt.SMALL_H) * scale
    Canvas(Modifier.size(w.dp, h.dp).alpha(if (enabled) 1f else 0.4f)
        .pointerInput(enabled) {
            detectTapGestures(onPress = {
                if (enabled) { st.press.set(0.88f); st.hover.set(1f); st.wake() }
                tryAwaitRelease()
                st.press.set(1f); st.hover.set(0f); st.wake()
            }, onTap = { if (enabled) { st.tap = 0f; st.wake(); onClick() } })
        }) {
        st.tick
        val glow: Float? = if (glowing) ((System.nanoTime() / 1e9) % 2.4 / 2.4).toFloat() else null
        val svg = CatArt.buttonSvg(kind, T.dark, hexColor(T.c1), name, st.hover.value, active, st.paw.value, st.press.value,
            st.tap, st.pop, glow, badge)
        drawSvgText(svg, 0f, 0f, size.width, size.height)
    }
}

// ---------------------------------------------------------------------------------------------------------
// Word mark and toast
// ---------------------------------------------------------------------------------------------------------

@Composable
fun WordMark(height: Dp) {
    val pm = painterResource(R.drawable.wordmark)
    val ratio = pm.intrinsicSize.width / pm.intrinsicSize.height
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.height(height).then(if (ratio > 0f && !ratio.isNaN()) Modifier.width(height * ratio) else Modifier.widthIn(min = 100.dp))
        .then(if (!T.dark) Modifier.clip(shape).background(rgb(15, 19, 24)).padding(horizontal = 5.dp, vertical = 3.dp) else Modifier)) {
        Image(pm, "NILKON", Modifier.fillMaxSize())
    }
}

@Composable
fun ToastPill(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(if (visible) 180 else 380), label = "toast")
    if (a < 0.01f) return
    val shape = RoundedCornerShape(40.dp)
    Row(modifier.alpha(a).clip(shape).background(if (T.dark) rgb(8, 14, 30, 0.94f) else Color(1f, 1f, 1f, 0.96f))
        .border(1.dp, ink(0.28f), shape).padding(start = 14.dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(T.c1))
        Spacer(Modifier.width(10.dp))
        Txt(text, 13, FontWeight.SemiBold, ink(0.95f), 2)
    }
}

@Suppress("unused")
private fun unusedW(c: ColumnScope, a: Animatable<Float, *>, p: PointerEventPass, f: Float) = abs(f) + 0
