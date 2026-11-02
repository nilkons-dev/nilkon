package app.nilkon.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.nilkon.AppModel
import app.nilkon.data.Store
import app.nilkon.playback.Engine
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

const val APP_NAME = "NILKON"
const val APP_VERSION = "1.7.4"
const val APP_TAGLINE = "Your music. Your frequency."

private val PAGES = listOf(
    Triple("playback", "Playback", "disc"), Triple("appearance", "Appearance", "palette"), Triple("visualizer", "Visualizer", "waves"),
    Triple("audio", "Audio", "eq"), Triple("online", "Online", "globe"), Triple("library", "Library", "library"), Triple("about", "About", "info"),
)

@Composable
fun SettingsRoot(m: AppModel) {
    val page = m.settingsPage
    if (page.isEmpty()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt("Settings", 24, FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            for ((k, label, icon) in PAGES) {
                val shape = RoundedCornerShape(16.dp)
                Row(Modifier.fillMaxWidth().clip(shape).background(cardFill).border(1.dp, cardEdge, shape)
                    .clickable(remember { MutableInteractionSource() }, null) { m.settingsPage = k }.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Ico(icon, 22, accentLight)
                    Spacer(Modifier.width(14.dp))
                    Txt(label, 16, FontWeight.SemiBold, textMain, 1, Modifier.weight(1f))
                    Ico("next", 16, ink(0.4f))
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton("prev", 44, 18, ink(0.85f)) { m.settingsPage = "" }
                Txt(PAGES.first { it.first == page }.second, 20, FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (page) {
                    "playback" -> PlaybackPage(m)
                    "appearance" -> AppearancePage(m)
                    "visualizer" -> VisualizerPage(m)
                    "audio" -> AudioPage(m)
                    "online" -> OnlineSettings(m)
                    "library" -> LibraryPage(m)
                    else -> AboutPage()
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---- building blocks -----------------------------------------------------------------------

@Composable
private fun Section(text: String) { Spacer(Modifier.height(10.dp)); Eyebrow(text); Spacer(Modifier.height(2.dp)) }

@Composable
private fun Note(text: String) { Txt(text, 12, FontWeight.Normal, textSub, 12) }

@Composable
private fun ToggleRow(key: String, title: String, desc: String, onChange: ((Boolean) -> Unit)? = null) {
    SettingRow(title, desc) { ToggleSwitch(Store.bool(key)) { Store.set(key, it); onChange?.invoke(it) } }
}

@Composable
private fun SliderRow(key: String, title: String, desc: String, lo: Int, hi: Int, bipolar: Boolean = false, fmt: (Int) -> String) {
    SettingRow(title, desc) {
        Row(Modifier.width(190.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassSlider(Store.int(key), lo, hi, Modifier.weight(1f).height(32.dp), bipolar) { Store.set(key, it) }
            Txt(fmt(Store.int(key)), 12, FontWeight.Bold, accentLight, 1, Modifier.width(52.dp), androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScrollCompat2(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun Modifier.horizontalScrollCompat2(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))

@Composable
private fun Tile(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, art: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.42f).clip(shape).border(if (selected) 2.dp else 1.dp, if (selected) T.c1 else ink(0.16f), shape), content = art)
        Spacer(Modifier.height(5.dp))
        Txt(label, 11, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) accentLight else ink(0.7f), 1)
    }
}

@Composable
private fun <E> Grid(items: List<E>, cols: Int = 3, cell: @Composable (E, Modifier) -> Unit) {
    items.chunked(cols).forEach { r ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            r.forEach { cell(it, Modifier.weight(1f)) }
            repeat(cols - r.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ---- pages -------------------------------------------------------------------------------------

@Composable
private fun PlaybackPage(m: AppModel) {
    Section("STARTING UP")
    ToggleRow("resume_last", "Resume where I stopped", "Opens your last song, paused, at the same position.")
    SettingRow("Open on", "The page NILKON shows when it starts.") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GButton("Home", null, "chip", active = Store.str("start_page") == "home") { Store.set("start_page", "home") }
            GButton("Online", null, "chip", active = Store.str("start_page") == "online") { Store.set("start_page", "online") }
        }
    }
    Section("PLAYING")
    ToggleRow("auto_radio", "Keep playing similar songs", "After an online song, similar songs follow automatically.")
    SliderRow("volume_step", "Volume step", "How much the − / + buttons change the volume.", 1, 20) { "$it%" }
    SliderRow("seek_step", "Skip step", "How far the skip buttons jump inside a song.", 3, 60) { "$it s" }
    Section("SLEEP TIMER")
    ChipRow {
        for ((mins, label) in listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 hour", 90 to "1.5 hours"))
            GButton(label, null, "chip", active = Engine.sleepMinutes == mins) { Engine.sleepIn(mins) }
    }
    Note("The music pauses by itself when the timer ends - handy at bedtime.")
    Section("NOTIFICATION AND LOCK SCREEN")
    Note("While music plays, the notification panel and the lock screen show play / pause, next, previous and a seek bar. Headset buttons and Bluetooth controls work too, even with the screen off.")
    GButton("Notification settings", "settings", "glass", iconSize = 16) { m.openAppSettings() }
}

@Composable
private fun AppearancePage(m: AppModel) {
    Section("MODE")
    ChipRow {
        GButton("Dark", "moon", "chip", active = Store.str("mode") == "dark", iconSize = 15) { Store.set("mode", "dark") }
        GButton("Light", "sun", "chip", active = Store.str("mode") == "light", iconSize = 15) { Store.set("mode", "light") }
    }
    Section("COLOUR THEME  ·  UI ACCENT AND SOUND WAVES")
    Grid(ACCENTS.keys.toList()) { name, mod ->
        val cols = ACCENTS.getValue(name)
        Tile(name, Store.str("accent") == name && !Store.bool("adaptive"), mod, { Store.set("accent", name) }) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(rgb(8, 12, 28), rgb(4, 7, 18))))
                val n = 14; val bw = size.width / (n * 1.5f)
                val br = Brush.horizontalGradient(listOf(cols.first, cols.second, cols.third))
                for (i in 0 until n) {
                    val v = 0.25f + 0.65f * abs(sin(i * 0.55f + 0.6f)) * (0.6f + 0.4f * cos(i * 0.21f))
                    val hh = size.height * 0.62f * v
                    drawRoundRect(br, Offset(bw * 0.6f + i * bw * 1.5f, size.height - 8.dp.toPx() - hh), Size(bw, hh), CornerRadius(bw / 2f))
                }
            }
        }
    }
    ToggleRow("adaptive", "Adapt colours to album art", "Tint the interface and waves with the colours of the playing cover.")
    Section("BACKGROUND")
    val bgs = BACKGROUNDS.map { it.first to it.second } + listOf("custom" to "Custom image")
    Grid(bgs) { (key, label), mod ->
        Tile(label, Store.str("background") == key, mod, { if (key == "custom") m.pickImage() else Store.set("background", key) }) {
            if (key == "custom" && Store.str("custom_bg").isEmpty()) {
                Box(Modifier.fillMaxSize().background(ink(0.06f)), contentAlignment = Alignment.Center) { Ico("image", 26, ink(0.6f)) }
            } else BackgroundPreview(key, Modifier.fillMaxSize())
        }
    }
    Note(if (Store.str("custom_bg").isEmpty()) "Tap “Custom image” to use one of your own pictures." else "Your own picture is saved inside NILKON.")
    SliderRow("bg_dim", "Image darkness", "Keeps text readable on bright pictures.", 0, 90) { "$it%" }
    SliderRow("bg_blur", "Image blur", "Soften the picture for a frosted look.", 0, 100) { "$it%" }
    Section("INTERFACE")
    ToggleRow("toasts", "Pop-up messages", "Small confirmations such as “Added to favourites”. Errors always show.")
    Note("The song name always appears in the notification panel and on the lock screen.")
}

@Composable
private fun VisualizerPage(m: AppModel) {
    Section("SOUND WAVE STYLE")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((key, label) in listOf("waves" to "Flowing waves", "bars" to "Spectrum bars", "mirror" to "Mirror bars")) {
            Tile(label, Store.str("wave_style") == key, Modifier.weight(1f), { Store.set("wave_style", key) }) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.linearGradient(listOf(rgb(8, 12, 28), rgb(4, 7, 18))))
                    val w = size.width; val h = size.height
                    val br = Brush.horizontalGradient(listOf(T.c1, T.c2, T.c3))
                    if (key == "waves") {
                        for (k in 0 until 3) {
                            val p = Path()
                            for (x in 0..40) {
                                val fx = x / 40f
                                val y = h * (0.62f - 0.08f * k) - h * 0.18f * sin(fx * 6.28f * (1.2f + 0.4f * k) + k) * (0.6f + 0.4f * sin(fx * 3.1f))
                                if (x == 0) p.moveTo(0f, y) else p.lineTo(fx * w, y)
                            }
                            drawPath(p, br, alpha = 0.9f - 0.25f * k, style = Stroke(2.dp.toPx()))
                        }
                    } else {
                        val n = 12; val bw = w / (n * 1.6f)
                        for (i in 0 until n) {
                            val v = 0.25f + 0.65f * abs(sin(i * 0.7f + 0.5f))
                            if (key == "mirror") { val a = h * 0.36f * v; drawRoundRect(br, Offset(bw * 0.5f + i * bw * 1.6f, h / 2f - a), Size(bw, a * 2f), CornerRadius(bw / 2f)) }
                            else { val a = h * 0.7f * v; drawRoundRect(br, Offset(bw * 0.5f + i * bw * 1.6f, h - 6.dp.toPx() - a), Size(bw, a), CornerRadius(bw / 2f)) }
                        }
                    }
                }
            }
        }
    }
    Note("The waves are computed from the real audio of the song you are playing.")
    Section("ONLINE SONGS")
    Note("Songs on this phone and downloaded songs are analysed from the file itself. For songs that stream from the internet, Android only lets an app read the sound through the “record audio” permission. NILKON never records anything - it only measures loudness to move the waves.")
    if (m.hasMic) Note("Real waves for streaming songs: on.") else GButton("Allow real waves", "waves", "glass", iconSize = 16) { m.requestMic() }
    Section("SYNC")
    SliderRow("sync_offset", "Wave timing", "If the waves feel early or late against the sound, nudge them here.", -400, 400, true) { "$it ms" }
    Section("RESPONSE")
    SliderRow("wave_gain", "Sensitivity", "How tall the waves grow for the same loudness.", 50, 170) { "$it%" }
    SliderRow("wave_smooth", "Smoothness", "Low = snappy and sharp, high = soft and flowing.", 0, 100) { "$it%" }
    Section("PERFORMANCE")
    ToggleRow("reduced_fx", "Reduced effects", "Lower animation rate for older phones.")
}

@Composable
private fun AudioPage(m: AppModel) {
    Section("EQUALIZER")
    Note(if (Store.eq.enabled) "Equalizer is on · preset: ${Store.eq.preset}" else "Equalizer is off")
    SettingRow("Equalizer", "Applies to every song that has no equalizer of its own.") {
        ToggleSwitch(Store.eq.enabled) { Store.eq = Store.eq.copy(enabled = it); Store.touch(); m.applyEq() }
    }
    GButton("Open full equalizer", "eq", "primary", iconSize = 17) { m.openEq(Engine.current, false) }
    Section("LOUDNESS")
    ToggleRow("normalize", "Even out loudness", "Keeps quiet and loud songs at a similar volume. Applies right away to songs that have been analysed.") { m.applyEq() }
    Note("Tip: use the ⋯ menu on any song and choose “Customize equalizer” to give that song its own sound.")
}

@Composable
private fun OnlineSettings(m: AppModel) {
    Section("DOWNLOADS")
    Note("Downloaded songs are saved in:\n${m.downloadDir().path}")
    ChipRow {
        GButton("M4A (original quality)", "download", "chip", active = true, iconSize = 15) { Store.set("download_format", "m4a") }
    }
    Note("Songs are saved in their original M4A quality. MP3 conversion is not available on Android; M4A plays in every music app.")
    SettingRow("Music engine", "If online playback or downloads stop working, refresh the engine. Updating NILKON itself brings the newest engine.") {
        GButton("Refresh", "reset", "glass", iconSize = 16) { m.updateEngine() }
    }
}

@Composable
private fun LibraryPage(m: AppModel) {
    m.rev
    Section("YOUR DATA")
    Note(m.libraryStats())
    SettingRow("Recently played", "Empties the history list on the Home page.") { GButton("Clear", "history", "glass", iconSize = 16) { m.clearRecent() } }
    SettingRow("Suggestions", "Forget how often you played songs so suggestions start fresh.") { GButton("Reset", "reset", "glass", iconSize = 16) { m.resetStats() } }
    Section("MUSIC ON THIS PHONE")
    Note(if (m.hasAudioPermission) "NILKON can read the music on this phone." else "NILKON does not have permission to read your music yet.")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!m.hasAudioPermission) GButton("Allow access", "check", "primary", iconSize = 16) { m.requestAudioAccess() }
        GButton("Rescan music", "reset", "glass", iconSize = 16) { m.scan() }
    }
    Spacer(Modifier.height(6.dp))
    Txt("Library file: ${Store.dataPath()}", 11, FontWeight.Normal, textEyebrow, 4)
}

@Composable
private fun AboutPage() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CatLogo(60.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Txt(APP_NAME, 24, FontWeight.ExtraBold, textMain, 1, spacing = 3f)
            Txt("$APP_TAGLINE  ·  Version $APP_VERSION", 12, FontWeight.Normal, textSub, 2)
        }
    }
    Spacer(Modifier.height(6.dp))
    Note("NILKON is a fast, private music player. Play the music you own, search and stream millions of songs online as you type, build playlists, save favourites and download tracks for offline listening - all in one clean interface. No account is needed: your library, playlists and listening history stay on this phone.")
    val groups = listOf(
        "LISTEN" to listOf("Live online search - results appear while you type, songs only",
            "Stream instantly, keep playing similar songs automatically, or download for offline",
            "Smart shuffle, repeat, sleep timer and a queue you can edit",
            "Resumes your last song and position the next time you open it"),
        "SOUND" to listOf("Studio 10-band equalizer with 24 presets, bass / treble tone controls and your own saved presets",
            "Smart headroom keeps boosted songs clean, with a separate setting for any single song",
            "Sound waves and seek bar drawn from the real spectrum of the song, beat by beat"),
        "YOUR LIBRARY" to listOf("Unlimited playlists with drag-handle ordering - local and online songs together",
            "Favourites, recently played and a home page that suggests what you love most",
            "Cover art, rename and delete"),
        "CONTROL" to listOf("Control playback from the notification panel, the lock screen, headset and Bluetooth buttons - even with the screen off",
            "Dark and light modes, colour themes and your own background picture"),
    )
    for ((title, items) in groups) {
        Section(title)
        for (t in items) Row(verticalAlignment = Alignment.Top) {
            Ico("check", 16, if (T.dark) lighter(T.c1, 120) else darker(T.c1, 115), Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(10.dp))
            Txt(t, 12, FontWeight.Normal, textSub, 6)
        }
    }
    Section("BUILT WITH")
    Note("Kotlin · Jetpack Compose · Media3 ExoPlayer · NewPipe Extractor · OkHttp · AndroidSVG")
    Spacer(Modifier.height(6.dp))
    Txt("Online features use publicly available music data for personal listening. You are responsible for following the terms of the services you use and local copyright law. NILKON is not affiliated with any streaming service.", 11, FontWeight.Normal, textEyebrow, 8)
}
