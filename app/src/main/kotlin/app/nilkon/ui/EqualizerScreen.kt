package app.nilkon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nilkon.AppModel
import app.nilkon.EqRequest
import app.nilkon.data.*
import java.util.Locale
import kotlin.math.roundToInt

val QUICK_PRESETS = listOf("Flat", "Bass Boost", "Vocal", "Pop", "Rock", "Electronic", "Night")
val BASS_W = floatArrayOf(1.0f, 0.9f, 0.6f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f)
val TREBLE_W = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.25f, 0.6f, 0.9f, 1.0f, 1.0f)

private fun freqLabel(hz: Int) = if (hz < 1000) "$hz Hz" else "${hz / 1000} kHz"
private fun fmtDb(v: Int) = if (v == 0) "0" else String.format(Locale.US, "%+d", v)

@Composable
fun EqualizerScreen(m: AppModel, req: EqRequest, onClose: () -> Unit) {
    var scope by remember { mutableStateOf(if (req.songScope && req.songKey != null) "song" else "global") }
    var songHas by remember { mutableStateOf(req.songKey != null && Store.trackEq.containsKey(req.songKey)) }
    var eq by remember { mutableStateOf(if (scope == "song") Store.eqFor(req.songKey ?: "") else Store.eq) }
    var bass by remember { mutableIntStateOf(0) }
    var treble by remember { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }   // refreshes the preset chips after custom presets change

    fun emit(n: EqState) {
        eq = n
        if (scope == "song" && req.songKey != null) { Store.setTrackEq(req.songKey, n); songHas = true } else { Store.eq = n; Store.touch() }
        m.applyEq()
    }
    fun load(name: String, resetPre: Boolean = false) {
        val c = Store.customPresets[name]
        val vals: List<Int>; var pre = eq.preamp
        if (c != null) { vals = c.values; pre = c.preamp } else vals = EQ_PRESETS[name] ?: return
        if (resetPre) pre = 0
        bass = 0; treble = 0
        emit(eq.copy(values = vals, preamp = pre, preset = name))
    }
    fun setScope(s: String) {
        if (s == scope || (s == "song" && req.songKey == null)) return
        scope = s; bass = 0; treble = 0
        eq = if (s == "song") Store.eqFor(req.songKey ?: "") else Store.eq
    }
    fun mark(n: EqState) = if (n.preset != "Custom") n.copy(preset = "Custom") else n

    fun presetsMenu() {
        val items = ArrayList<MenuItem>()
        for (n in EQ_PRESETS.keys) items += MenuItem("eq", n, checked = n == eq.preset) { load(n) }
        if (Store.customPresets.isNotEmpty()) {
            items += MenuItem.divider()
            for (n in Store.customPresets.keys.toList()) items += MenuItem("list-music", "$n   (mine)", checked = n == eq.preset) { load(n) }
            items += MenuItem.divider()
            items += MenuItem("trash", "Delete my preset…", danger = true) {
                m.menu = MenuSpec("Delete my preset", "", Store.customPresets.keys.toList().map { n ->
                    MenuItem("trash", n, danger = true) {
                        Store.customPresets.remove(n); Store.touch()
                        if (eq.preset == n) eq = eq.copy(preset = "Custom")
                        rev++
                    }
                })
            }
        }
        m.menu = MenuSpec("Presets", "", items)
    }
    fun savePreset() {
        m.prompt = PromptSpec("Save preset", "Name this equalizer setting so you can use it again.", "e.g. My car", "", "Save", "list-plus") { raw ->
            val name = raw.trim().take(40)
            if (name.isEmpty()) return@PromptSpec
            if (EQ_PRESETS.containsKey(name)) {
                m.notice = NoticeSpec("Name already used", "That is the name of a built-in preset. Please choose another one.")
            } else {
                Store.customPresets[name] = EqPreset(eq.values.toList(), eq.preamp); Store.touch()
                rev++
                emit(eq.copy(preset = name))
            }
        }
    }

    val enabled = eq.enabled
    Column(Modifier.fillMaxSize().background(pageBg.a(0.97f)).windowInsetsPadding(WindowInsets.systemBars).verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico("eq", 24, if (T.dark) lighter(T.c1, 115) else darker(T.c1, 115))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt("Equalizer", 20, FontWeight.ExtraBold)
                Txt(if (scope == "song") "“${req.songTitle}” " + (if (songHas) "has its own equalizer" else "uses the all-songs equalizer until you change something")
                    else "10-band studio equalizer · applies to every song without its own setting", 11, FontWeight.Normal, textSub, 3)
            }
            ToggleSwitch(enabled) { emit(eq.copy(enabled = it)) }
            Spacer(Modifier.width(6.dp))
            IconButton("x", 40, 16, ink(0.8f)) { onClose() }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GButton("All songs", "library", "chip", active = scope == "global", iconSize = 14) { setScope("global") }
            if (req.songKey != null) {
                val short = if (req.songTitle.length <= 24) req.songTitle else req.songTitle.take(23) + "…"
                GButton("This song: $short", "music", "chip", active = scope == "song", iconSize = 14) { setScope("song") }
            }
            if (scope == "song" && songHas) GButton("Use all-songs EQ", "reset", "ghost", iconSize = 15) {
                Store.setTrackEq(req.songKey!!, null); songHas = false; eq = Store.eq; bass = 0; treble = 0; m.applyEq()
            }
        }
        rev
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (n in QUICK_PRESETS) GButton(n, null, "chip", active = n == eq.preset) { load(n) }
            val other = eq.preset !in QUICK_PRESETS && eq.preset != "Custom" && eq.preset.isNotEmpty()
            GButton(if (other) eq.preset else "All presets", "list-music", "chip", active = other, iconSize = 14) { presetsMenu() }
            GButton("Save preset", "plus", "ghost", iconSize = 15) { savePreset() }
            GButton("Reset", "reset", "ghost", iconSize = 15) { load("Flat", true) }
        }
        // tone
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("Bass", 13, FontWeight.Medium, textSub, 1, Modifier.width(44.dp))
            GlassSlider(bass, -10, 10, Modifier.weight(1f).height(32.dp), bipolar = true, enabled = enabled) { v ->
                val d = v - bass; bass = v
                emit(mark(eq.copy(values = eq.values.mapIndexed { i, x -> (x + d * BASS_W[i]).roundToInt().coerceIn(-20, 20) })))
            }
            Spacer(Modifier.width(14.dp))
            Txt("Treble", 13, FontWeight.Medium, textSub, 1, Modifier.width(48.dp))
            GlassSlider(treble, -10, 10, Modifier.weight(1f).height(32.dp), bipolar = true, enabled = enabled) { v ->
                val d = v - treble; treble = v
                emit(mark(eq.copy(values = eq.values.mapIndexed { i, x -> (x + d * TREBLE_W[i]).roundToInt().coerceIn(-20, 20) })))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("Smart headroom", 13, FontWeight.Medium, textSub, 1)
            Spacer(Modifier.width(10.dp))
            ToggleSwitch(eq.safe, enabled) { emit(eq.copy(safe = it)) }
            Spacer(Modifier.weight(1f))
            val eff = eq.effectivePreamp() - eq.preamp
            if (eq.safe && eff != 0f) Txt(String.format(Locale.US, "headroom %+.1f dB", eff), 11, FontWeight.Normal, textEyebrow)
        }
        EqCurve(Modifier.fillMaxWidth().height(150.dp), eq.values, enabled) { i, v ->
            emit(mark(eq.copy(values = eq.values.toMutableList().also { it[i] = v })))
        }
        // preamp + 10 band columns
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            @Composable
            fun Column1(label: String, value: Int, w: Modifier, onV: (Int) -> Unit) {
                Column(w, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt(fmtDb(value), 11, FontWeight.Bold, if (value != 0 && enabled) accentLight else ink(0.6f), 1, align = TextAlign.Center)
                    GlassSlider(value, -20, 20, Modifier.height(150.dp).width(30.dp), bipolar = true, vertical = true, enabled = enabled, onChange = onV)
                    Txt(label, 9, FontWeight.Medium, textEyebrow, 1, align = TextAlign.Center)
                }
            }
            Column1("PRE", eq.preamp, Modifier.weight(1f)) { emit(eq.copy(preamp = it)) }
            Spacer(Modifier.width(6.dp))
            EQ_FREQS.forEachIndexed { i, hz ->
                Column1(freqLabel(hz), eq.values[i], Modifier.weight(1f)) { v ->
                    emit(mark(eq.copy(values = eq.values.toMutableList().also { it[i] = v })))
                }
            }
        }
        Txt("Drag the curve or sliders · double-tap a slider to reset it · presets can be saved as your own", 11, FontWeight.Normal, textEyebrow, 3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GButton("Done", "check", "primary", iconSize = 16) { onClose() } }
        Spacer(Modifier.height(16.dp))
    }
}
