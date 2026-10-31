package app.nilkon.ui

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Kotlin port of the desktop cat_art.py: every function returns an SVG string for the current animation state.
 * The art is drawn with AndroidSVG, so the cat looks and moves exactly like in the Windows player.
 */
object CatArt {
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    fun smooth(x: Float): Float = smoothstep(x)
    fun easeOut(x: Float): Float { val t = clamp01(x); return 1f - (1f - t).pow(3) }

    /** Piece-wise keyframes [(percent, value) ...] with eased segments. */
    fun kf(points: List<Pair<Float, Float>>, pct: Float): Float {
        if (pct <= points[0].first) return points[0].second
        for (i in 0 until points.size - 1) {
            val p0 = points[i].first; val v0 = points[i].second
            val p1 = points[i + 1].first; val v1 = points[i + 1].second
            if (pct <= p1) {
                val span = if (p1 - p0 == 0f) 1f else p1 - p0
                return lerp(v0, v1, smooth((pct - p0) / span))
            }
        }
        return points.last().second
    }

    private fun pts(vararg v: Float): List<Pair<Float, Float>> {
        val out = ArrayList<Pair<Float, Float>>()
        var i = 0
        while (i + 1 < v.size) { out.add(Pair(v[i], v[i + 1])); i += 2 }
        return out
    }

    /** CSS cubic-bezier(x1, y1, x2, y2) evaluated at time fraction u. */
    fun bezier(x1: Float, y1: Float, x2: Float, y2: Float, u0: Float): Float {
        val u = clamp01(u0)
        var lo = 0f; var hi = 1f; var t = u
        for (n in 0 until 24) {
            val x = 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t
            if (x < u) lo = t else hi = t
            t = (lo + hi) / 2f
        }
        return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t
    }

    fun tf(tx: Float = 0f, ty: Float = 0f, rot: Float = 0f, sx: Float = 1f, sy: Float = 1f, ox: Float = 0f, oy: Float = 0f): String =
        "translate(${fmt(tx + ox)} ${fmt(ty + oy)}) rotate(${fmt(rot)}) scale(${fmt(sx, 4)} ${fmt(sy, 4)}) translate(${fmt(-ox)} ${fmt(-oy)})"

    private const val NOTE = "<ellipse rx=\"11\" ry=\"8\" transform=\"rotate(-20)\"/><rect x=\"8\" y=\"-38\" width=\"4\" height=\"38\"/>" +
        "<path d=\"M12 -38q18 4 14 24q-2 -12 -14 -14z\"/>"
    private const val HEART = "M0 -6C-10 -20 -30 -4 0 18C30 -4 10 -20 0 -6Z"

    /** Loop phase 0..1 of an endless animation with a start delay; null while it still waits. */
    private fun cycle(t: Float, period: Float, delay: Float = 0f): Float? {
        if (t < delay) return null
        return (((t - delay) % period) / period)
    }

    private val EAR_WIGGLE = pts(0f, 0f, 78f, 0f, 84f, -12f, 90f, 3f, 100f, 0f)
    private val WHISKER = pts(0f, 0f, 70f, 0f, 78f, -4f, 86f, 3f, 100f, 0f)

    fun logoSvg(t: Float, lookX: Float, lookY: Float, sleep: Float, blink: Float?, tap: Float?, reduced: Boolean): String {
        val cx = lookX; val cy = lookY
        val asleep = sleep > 0.5f
        val happy = tap != null && tap < 1.1f
        var catTf = ""
        if (happy) {
            val u = clamp01(tap!! / 0.75f)
            val sx = kf(pts(0f, 1f, 18f, 1.09f, 42f, .95f, 68f, 1.03f, 100f, 1f), u * 100f)
            val sy = kf(pts(0f, 1f, 18f, .88f, 42f, 1.08f, 68f, .97f, 100f, 1f), u * 100f)
            val ty = kf(pts(0f, 0f, 18f, 0f, 42f, -16f, 68f, 0f, 100f, 0f), u * 100f)
            catTf = tf(0f, ty, 0f, sx, sy, 256f, 330f)
        } else if (asleep) {
            val ph = (t % 3.2f) / 3.2f
            val k = smooth(1f - abs(ph * 2f - 1f))
            val s = 1f + 0.025f * k
            catTf = tf(0f, 3f * k, 0f, s, s, 256f, 330f)
        } else if (!reduced) {
            val ph = (t % 0.8f) / 0.8f
            val k = smooth(1f - abs(ph * 2f - 1f))
            catTf = tf(0f, 7f * k, 0f, 1f, 1f, 256f, 330f)
        }
        var eL = 0f; var eR = 0f
        if (happy && tap!! < 0.6f) {
            val p = tap / 0.6f * 100f
            eL = kf(pts(0f, 0f, 25f, -16f, 50f, 6f, 75f, -8f, 100f, 0f), p)
            eR = kf(pts(0f, 0f, 25f, 16f, 50f, -6f, 75f, 8f, 100f, 0f), p)
        } else if (!asleep && !happy && !reduced) {
            val a = cycle(t, 3.4f); val b = cycle(t, 3.4f, 0.5f)
            if (a != null) eL = kf(EAR_WIGGLE, a * 100f)
            if (b != null) eR = -kf(EAR_WIGGLE, b * 100f)
        }
        var wk1 = 0f; var wk2 = 0f; var dots = 1f; var w1 = 0.15f; var w2 = 0.15f
        val notes = ArrayList<FloatArray>()
        if (!asleep && !reduced) {
            val a = cycle(t, 4f); val b = cycle(t, 4f, 0.3f)
            if (a != null) wk1 = kf(WHISKER, a * 100f)
            if (b != null) wk2 = kf(WHISKER, b * 100f)
            val ph = (t % 0.8f) / 0.8f
            dots = 1f + 0.3f * smooth(1f - abs(ph * 2f - 1f))
            w1 = lerp(.15f, .9f, smooth(1f - abs(ph * 2f - 1f)))
            if (t >= 0.2f) {
                val ph2 = ((t - 0.2f) % 0.8f) / 0.8f
                w2 = lerp(.15f, .9f, smooth(1f - abs(ph2 * 2f - 1f)))
            } else w2 = 1f
            for (delay in floatArrayOf(0f, 1f, 2f)) {
                val c = cycle(t, 3f, delay)
                if (c == null) { notes.add(floatArrayOf(0f, 1f, 1f)); continue }
                val e = easeOut(c)
                val ty = lerp(30f, -40f, e)
                val sc = lerp(.6f, 1f, e)
                val op = if (c < .2f) easeOut(c / .2f) else 1f - easeOut((c - .2f) / .8f)
                notes.add(floatArrayOf(ty, sc, op))
            }
        } else {
            for (i in 0 until 3) notes.add(floatArrayOf(0f, 1f, 0f))
        }
        val eyeOpen = 1f - maxOf(sleep, if (happy) 1f else 0f)
        val sy = if (blink == null) 1f else kf(pts(0f, 1f, 45f, .06f, 100f, 1f), blink * 100f)
        var shutOp = if (!happy) sleep else 0f
        val joyOp = if (happy) 1f else 0f
        if (happy) shutOp = 0f
        val browTy = if (!happy) 8f * sleep else -6f
        var px = cx * 7f; var py = cy * 8f
        val nx = px / 6f; val ny = py / 9f
        val r = hypot(nx, ny)
        if (r > 1f) { px /= r; py /= r }
        val st = sleep

        fun eye(x: Int): String =
            "<g opacity=\"${fmt(eyeOpen)}\" transform=\"${tf(0f, 0f, 0f, 1f, sy, x.toFloat(), 298f)}\">" +
            "<ellipse cx=\"$x\" cy=\"298\" rx=\"29\" ry=\"32\" fill=\"#fff4ea\" stroke=\"#0f1318\" stroke-width=\"5\"/>" +
            "<g transform=\"translate(${fmt(px, 2)} ${fmt(py, 2)})\"><circle cx=\"$x\" cy=\"300\" r=\"23\" fill=\"#8fd14f\"/>" +
            "<circle cx=\"$x\" cy=\"300\" r=\"22\" fill=\"none\" stroke=\"#4e8f2a\" stroke-width=\"3\"/>" +
            "<circle cx=\"$x\" cy=\"300\" r=\"13\" fill=\"#0f1318\"/><circle cx=\"${x - 8}\" cy=\"293\" r=\"5.5\" fill=\"#fff\"/>" +
            "<circle cx=\"${x + 9}\" cy=\"309\" r=\"2.5\" fill=\"#fff\" opacity=\".8\"/></g>" +
            "<path d=\"M${x - 21} 277A29 32 0 0 1 ${x + 21} 277Z\" fill=\"#ff9468\" stroke=\"#0f1318\" " +
            "stroke-width=\"5\" stroke-linejoin=\"round\"/></g>"

        val headTf = "translate(${fmt(cx * 6f, 2)} ${fmt(cy * 5f + st * 8f, 2)}) rotate(${fmt(cx * 5f + st * 6f, 2)} 256 330)"
        val faceTf = "translate(${fmt(cx * 10f, 2)} ${fmt(cy * 7f, 2)})"

        val noteX = arrayOf(intArrayOf(70, 130), intArrayOf(448, 118), intArrayOf(425, 62))
        val noteS = floatArrayOf(1.0f, 1.15f, 0.8f)
        val noteC = arrayOf("#ff9468", "#ff9468", "#ffb596")
        val notesSvg = StringBuilder()
        for (i in 0 until 3) {
            val x = noteX[i][0]; val y = noteX[i][1]
            val n = notes[i]
            notesSvg.append("<g opacity=\"${fmt(n[2])}\" transform=\"translate(0 ${fmt(n[0], 2)}) ${tf(0f, 0f, 0f, n[1], n[1], x.toFloat(), y.toFloat())}\">" +
                "<g fill=\"${noteC[i]}\" transform=\"translate($x $y) scale(${fmt(noteS[i], 2)})\">$NOTE</g></g>")
        }
        val zs = StringBuilder()
        if (asleep) {
            val zp = arrayOf(intArrayOf(392, 150), intArrayOf(410, 120), intArrayOf(430, 92))
            for (i in 0 until 3) {
                val c = cycle(t, 3f, i.toFloat()) ?: continue
                val zx = zp[i][0]; val zy = zp[i][1]
                val tx = 22f * easeOut(c)
                val ty = lerp(22f, -44f, easeOut(c))
                val sc = lerp(.5f, 1.15f, easeOut(c))
                val op = if (c < .25f) .95f * easeOut(c / .25f) else .95f * (1f - easeOut((c - .25f) / .75f))
                zs.append("<text x=\"$zx\" y=\"$zy\" opacity=\"${fmt(op)}\" fill=\"#ff9468\" font-size=\"34\" font-weight=\"bold\" " +
                    "font-family=\"Segoe UI, Arial, sans-serif\" transform=\"translate(${fmt(tx, 2)} ${fmt(ty, 2)}) " +
                    "${tf(0f, 0f, 0f, sc, sc, (zx + 9).toFloat(), (zy - 12).toFloat())}\">z</text>")
            }
        }
        val hearts = StringBuilder()
        if (happy) {
            val hp = arrayOf(intArrayOf(180, 120), intArrayOf(256, 92), intArrayOf(332, 120))
            val hd = floatArrayOf(0f, 0.12f, 0.24f)
            for (i in 0 until 3) {
                val c = (tap!! - hd[i]) / 1.1f
                if (c <= 0f || c >= 1f) continue
                val e = easeOut(c)
                val ty = lerp(12f, -58f, e)
                val sc = lerp(.3f, 1.1f, e)
                val op = if (c < .25f) easeOut(c / .25f) else 1f - easeOut((c - .25f) / .75f)
                hearts.append("<g transform=\"translate(${hp[i][0]} ${hp[i][1]})\"><path d=\"$HEART\" fill=\"#ff6f8a\" opacity=\"${fmt(op)}\" " +
                    "transform=\"translate(0 ${fmt(ty, 2)}) ${tf(0f, 0f, 0f, sc, sc, 0f, 4f)}\"/></g>")
            }
        }
        fun wave(d: String, op: Float) = "<path d=\"$d\" opacity=\"${fmt(op)}\"/>"
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 512 512\" width=\"512\" height=\"512\">\n" +
            "$notesSvg$zs\n<g transform=\"$catTf\">\n" +
            "<g fill=\"none\" stroke=\"#ff9468\" stroke-width=\"7\" stroke-linecap=\"round\">\n" +
            wave("M56 276q-14 33 0 66", w1) + wave("M36 258q-26 51 0 102", w2) + "\n" +
            wave("M456 276q14 33 0 66", w1) + wave("M476 258q26 51 0 102", w2) + "</g>\n" +
            "<g transform=\"$headTf\">\n" +
            "<g transform=\"${tf(0f, 0f, eL, 1f, 1f, 174f, 250f)}\"><path d=\"M132 250L126 112L222 184Z\" fill=\"#ff9468\" stroke=\"#ff9468\" stroke-width=\"14\" stroke-linejoin=\"round\"/><path d=\"M150 214L148 148L196 184Z\" fill=\"#ffb596\"/><path d=\"M152 214l10 -10l4 10l8 -8l4 10z\" fill=\"#ffe3cf\"/></g>\n" +
            "<g transform=\"${tf(0f, 0f, eR, 1f, 1f, 338f, 250f)}\"><path d=\"M380 250L386 112L290 184Z\" fill=\"#ff9468\" stroke=\"#ff9468\" stroke-width=\"14\" stroke-linejoin=\"round\"/><path d=\"M362 214L364 148L316 184Z\" fill=\"#ffb596\"/><path d=\"M360 214l-10 -10l-4 10l-8 -8l-4 10z\" fill=\"#ffe3cf\"/></g>\n" +
            "<g fill=\"#ff9468\" stroke=\"#ff9468\" stroke-width=\"8\" stroke-linejoin=\"round\"><path d=\"M172 370L146 382L178 390Z\"/><path d=\"M184 392L162 412L196 408Z\"/><path d=\"M340 370L366 382L334 390Z\"/><path d=\"M328 392L350 412L316 408Z\"/></g>\n" +
            "<ellipse cx=\"256\" cy=\"292\" rx=\"144\" ry=\"118\" fill=\"#ff9468\"/>\n" +
            "<g transform=\"$faceTf\">\n" +
            "<g stroke=\"#c9582c\" stroke-width=\"9\" stroke-linecap=\"round\" fill=\"none\"><path d=\"M256 188v38\"/><path d=\"M228 196q8 14 5 30\"/><path d=\"M284 196q-8 14 -5 30\"/></g>\n" +
            "<ellipse cx=\"238\" cy=\"348\" rx=\"27\" ry=\"21\" fill=\"#ffe3cf\"/><ellipse cx=\"274\" cy=\"348\" rx=\"27\" ry=\"21\" fill=\"#ffe3cf\"/>\n" +
            "<g fill=\"#d98a63\"><circle cx=\"224\" cy=\"342\" r=\"2.2\"/><circle cx=\"233\" cy=\"348\" r=\"2.2\"/><circle cx=\"222\" cy=\"353\" r=\"2.2\"/><circle cx=\"288\" cy=\"342\" r=\"2.2\"/><circle cx=\"279\" cy=\"348\" r=\"2.2\"/><circle cx=\"290\" cy=\"353\" r=\"2.2\"/></g>\n" +
            "<g transform=\"rotate(28 172 352)\"><rect x=\"152\" y=\"344\" width=\"40\" height=\"15\" rx=\"4\" fill=\"#f3d6b0\" stroke=\"#c9a47a\" stroke-width=\"2\"/><rect x=\"164\" y=\"344\" width=\"16\" height=\"15\" fill=\"#e6bd8b\"/></g>\n" +
            "<g transform=\"translate(0 ${fmt(browTy, 2)})\" fill=\"none\" stroke=\"#8a3d1f\" stroke-width=\"6.5\" stroke-linecap=\"round\"><path d=\"M180 254Q204 244 230 252\"/><path d=\"M282 252Q308 244 332 254\"/></g>\n" +
            eye(206) + "\n" +
            "<path d=\"M180 300q26 20 52 0\" opacity=\"${fmt(shutOp)}\" fill=\"none\" stroke=\"#0f1318\" stroke-width=\"10\" stroke-linecap=\"round\"/>\n" +
            "<path d=\"M180 306q26 -26 52 0\" opacity=\"${fmt(joyOp)}\" fill=\"none\" stroke=\"#0f1318\" stroke-width=\"10\" stroke-linecap=\"round\"/>\n" +
            eye(306) + "\n" +
            "<path d=\"M280 300q26 20 52 0\" opacity=\"${fmt(shutOp)}\" fill=\"none\" stroke=\"#0f1318\" stroke-width=\"10\" stroke-linecap=\"round\"/>\n" +
            "<path d=\"M280 306q26 -26 52 0\" opacity=\"${fmt(joyOp)}\" fill=\"none\" stroke=\"#0f1318\" stroke-width=\"10\" stroke-linecap=\"round\"/>\n" +
            "<path d=\"M243 334q13 -7 26 0q-2 11 -13 15q-11 -4 -13 -15z\" fill=\"#0f1318\"/><ellipse cx=\"250\" cy=\"337\" rx=\"4\" ry=\"1.8\" fill=\"#fff\" opacity=\".5\"/>\n" +
            "<path d=\"M256 349v6M256 355q-12 13 -27 4M256 355q12 13 27 4\" fill=\"none\" stroke=\"#0f1318\" stroke-width=\"5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n" +
            "<g transform=\"${tf(0f, 0f, wk1, 1f, 1f, 177f, 349f)}\" fill=\"none\" stroke=\"#7a3f26\" stroke-width=\"3.5\" stroke-linecap=\"round\" opacity=\".7\"><path d=\"M204 338l-48 -10M202 348l-52 2M204 358l-46 12\"/></g>\n" +
            "<g transform=\"${tf(0f, 0f, wk2, 1f, 1f, 335f, 349f)}\" fill=\"none\" stroke=\"#7a3f26\" stroke-width=\"3.5\" stroke-linecap=\"round\" opacity=\".7\"><path d=\"M308 338l48 -10M310 348l52 2M308 358l46 12\"/></g>\n" +
            "</g>\n" +
            "<path d=\"M114 300C104 118 408 118 398 300\" fill=\"none\" stroke=\"#1a1f26\" stroke-width=\"20\" stroke-linecap=\"round\"/>\n" +
            "<path d=\"M114 300C104 118 408 118 398 300\" fill=\"none\" stroke=\"#ff9468\" stroke-width=\"3\" stroke-linecap=\"round\" opacity=\".55\" transform=\"translate(0 -1)\"/>\n" +
            "<rect x=\"78\" y=\"256\" width=\"62\" height=\"106\" rx=\"28\" fill=\"#1a1f26\" stroke=\"#ff9468\" stroke-width=\"6\"/>\n" +
            "<rect x=\"372\" y=\"256\" width=\"62\" height=\"106\" rx=\"28\" fill=\"#1a1f26\" stroke=\"#ff9468\" stroke-width=\"6\"/>\n" +
            "<circle cx=\"109\" cy=\"309\" r=\"11\" fill=\"#ff9468\" transform=\"${tf(0f, 0f, 0f, dots, dots, 109f, 309f)}\"/><circle cx=\"403\" cy=\"309\" r=\"11\" fill=\"#ff9468\" transform=\"${tf(0f, 0f, 0f, dots, dots, 403f, 309f)}\"/>\n" +
            "</g></g>$hearts</svg>"
    }

    // ---- player buttons ---------------------------------------------------------------------------------
    const val SMALL_W = 42f
    const val SMALL_H = 65f
    const val PLAY_W = 94f
    const val PLAY_H = 90f

    private fun mix(c1: String, c2: String, t: Float): String {
        val a = IntArray(3) { c1.substring(1 + it * 2, 3 + it * 2).toInt(16) }
        val b = IntArray(3) { c2.substring(1 + it * 2, 3 + it * 2).toInt(16) }
        val o = IntArray(3) { Math.round(lerp(a[it].toFloat(), b[it].toFloat(), clamp01(t))) }
        return String.format(java.util.Locale.US, "#%02x%02x%02x", o[0], o[1], o[2])
    }

    private fun ear(x: Float, y: Float, w: Float, h: Float, left: Boolean, rot: Float, color: String): String {
        val px = if (left) 0.22f * w else 0.78f * w
        val d = "${fmt(x, 2)},${fmt(y + h, 2)} ${fmt(x + px, 2)},${fmt(y, 2)} ${fmt(x + w, 2)},${fmt(y + h, 2)}"
        return "<polygon points=\"$d\" fill=\"$color\" transform=\"${tf(0f, 0f, rot, 1f, 1f, x + w / 2f, y + h)}\"/>"
    }

    /** kind: toggle | prev | next | play. */
    fun buttonSvg(kind: String, dark: Boolean, accent: String, icon: String?, hover: Float, on: Boolean, paw: Float,
                  press: Float, tap: Float?, pop: Float?, glow: Float?, badge: String): String {
        val btn = if (dark) "#1a1f26" else "#e7dfd6"
        val dim = if (dark) "#8a929c" else "#6b625a"
        val inkc = if (dark) "#0f1318" else "#ffffff"
        val ic = if (dark) "#cfd5dc" else "#4a423b"
        val ac = accent
        val w: Float; val h: Float; val ccx: Float; val ccy: Float; val rad: Float
        if (kind == "play") { w = PLAY_W; h = PLAY_H; ccx = 47f; ccy = 45f; rad = 33f }
        else { w = SMALL_W; h = SMALL_H; ccx = 21f; ccy = 30f; rad = 21f }
        val out = StringBuilder()
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ${fmt(w, 0)} ${fmt(h, 0)}\" width=\"${fmt(w, 0)}\" height=\"${fmt(h, 0)}\">\n")
        if (kind == "play" && glow != null) {
            val k = smooth(1f - abs(glow * 2f - 1f))
            val spread = 9f * k
            out.append("<circle cx=\"${fmt(ccx, 1)}\" cy=\"${fmt(ccy, 1)}\" r=\"${fmt(rad + spread, 2)}\" fill=\"$ac\" opacity=\"${fmt(0.25f * (1f - k))}\"/>\n")
        }
        out.append("<g transform=\"${tf(0f, 0f, 0f, press, press, ccx, ccy)}\">\n")
        val u = tap ?: 99f
        val icol: String
        val isz: Float
        if (kind == "play") {
            var earL = 0f; var earR = 0f
            if (u < .45f) {
                val p = u / .45f * 100f
                earL = kf(pts(0f, 0f, 40f, -16f, 70f, 5f, 100f, 0f), p)
                earR = kf(pts(0f, 0f, 40f, 16f, 70f, -5f, 100f, 0f), p)
            }
            val x0 = ccx - rad; val y0 = ccy - rad
            out.append(ear(x0 + 7f, y0 - 9f, 22f, 22f, true, earL, ac)).append("\n")
            out.append(ear(x0 + rad * 2f - 7f - 22f, y0 - 9f, 22f, 22f, false, earR, ac)).append("\n")
            out.append("<circle cx=\"${fmt(ccx, 1)}\" cy=\"${fmt(ccy, 1)}\" r=\"${fmt(rad, 1)}\" fill=\"$ac\"/>\n")
            var wsx = 1f; var wrot = 0f
            if (u < .45f) {
                val p = u / .45f * 100f
                wsx = kf(pts(0f, 1f, 40f, 1.08f, 70f, .98f, 100f, 1f), p)
                wrot = kf(pts(0f, 0f, 40f, -3f, 70f, 2f, 100f, 0f), p)
            }
            out.append("<g transform=\"translate(${fmt(ccx - 47f, 1)} ${fmt(ccy - 12f, 1)}) ${tf(0f, 0f, wrot, wsx, 1f, 47f, 12f)}\" fill=\"none\" " +
                "stroke=\"$ac\" stroke-width=\"1.6\" stroke-linecap=\"round\" opacity=\".55\"><path d=\"$WHISK_PATH\"/></g>\n")
            icol = inkc; isz = 27f
        } else {
            val base: String
            if (kind == "prev" || kind == "next") {
                var earL = 0f; var earR = 0f
                if (u < .45f) {
                    val p = u / .45f * 100f
                    earL = kf(pts(0f, 0f, 40f, -16f, 70f, 5f, 100f, 0f), p)
                    earR = kf(pts(0f, 0f, 40f, 16f, 70f, -5f, 100f, 0f), p)
                }
                out.append(ear(3f, 30f - 21f - 5f, 13f, 13f, true, earL, btn)).append("\n")
                out.append(ear(42f - 3f - 13f, 30f - 21f - 5f, 13f, 13f, false, earR, btn)).append("\n")
                out.append("<circle cx=\"${fmt(ccx, 1)}\" cy=\"${fmt(ccy, 1)}\" r=\"${fmt(rad, 1)}\" fill=\"$btn\"/>\n")
                base = ic
            } else {
                base = if (on) ac else dim
            }
            icol = if (kind != "toggle" || !on) mix(base, ac, hover) else ac
            isz = 20f
        }
        var name = icon ?: kind
        if (kind == "toggle" && !BTN_ICONS.containsKey(name)) name = "shuffle"
        var ix = 0f; var irot = 0f; var isc = 1f; var iop = 1f
        if (kind == "prev" && u < .35f) ix = kf(pts(0f, 0f, 40f, -5f, 100f, 0f), u / .35f * 100f)
        if (kind == "next" && u < .35f) ix = kf(pts(0f, 0f, 40f, 5f, 100f, 0f), u / .35f * 100f)
        if (kind == "toggle" && name == "shuffle" && u < .4f) irot = kf(pts(0f, 0f, 25f, -14f, 60f, 12f, 100f, 0f), u / .4f * 100f)
        if (kind == "toggle" && name == "repeat" && u < .5f) irot = 360f * bezier(.4f, 0f, .2f, 1f, u / .5f)
        if (kind == "play" && pop != null && pop < 1f) {
            isc = lerp(.4f, 1f, bezier(.3f, 1.8f, .5f, 1f, pop))
            iop = clamp01(pop / .15f)
        }
        val s = isz / 24f
        val cxi = ccx + ix; val cyi = ccy
        out.append("<g opacity=\"${fmt(iop)}\" fill=\"$icol\" transform=\"translate(${fmt(cxi, 2)} ${fmt(cyi, 2)}) rotate(${fmt(irot, 2)}) " +
            "scale(${fmt(isc, 4)}) translate(${fmt(-isz / 2f, 2)} ${fmt(-isz / 2f, 2)}) scale(${fmt(s, 4)})\">${BTN_ICONS[name] ?: ""}</g>\n")
        if (badge.isNotEmpty() && kind == "toggle") {
            out.append("<text x=\"${fmt(ccx + 7f, 1)}\" y=\"${fmt(ccy + 5f, 1)}\" font-size=\"9\" font-weight=\"bold\" fill=\"$icol\" " +
                "font-family=\"Segoe UI, Arial, sans-serif\" text-anchor=\"middle\">$badge</text>\n")
        }
        if (kind == "toggle" && paw > 0.01f) {
            val px = ccx - 5.5f; val py = ccy + 21f + 12f - 11f
            out.append("<g fill=\"$ac\" transform=\"translate(${fmt(px + 5.5f, 2)} ${fmt(py + 5.5f, 2)}) scale(${fmt(paw)}) " +
                "translate(-5.5 -5.5) scale(${fmt(11f / 24f, 4)})\">$PAW_SVG</g>\n")
        }
        out.append("</g></svg>")
        return out.toString()
    }

    @Suppress("unused")
    private fun unused() = sqrt(1.0)
}
