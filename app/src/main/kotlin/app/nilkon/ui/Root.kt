package app.nilkon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.nilkon.AppModel
import app.nilkon.data.Covers
import app.nilkon.data.Store
import app.nilkon.playback.Engine
import kotlinx.coroutines.delay

@Composable
fun NilkonRoot(m: AppModel, onDarkChanged: (Boolean) -> Unit) {
    val cur = Engine.current
    val mode = Store.str("mode")
    val accent = Store.str("accent")
    val adaptive = Store.bool("adaptive")
    val owner = LocalLifecycleOwner.current

    // ---- settings -> theme and visuals -----------------------------------------------------------
    LaunchedEffect(mode) { T.dark = mode != "light"; onDarkChanged(T.dark) }
    val cover = Covers.peek(cur, 200)
    var hue by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(cover) { hue = cover?.let { Covers.hue(it) } }
    LaunchedEffect(accent, adaptive, hue, cur?.key) {
        val h = hue
        if (adaptive && cur != null && h != null) T.setHueTargets(h) else T.setAccent(accent)
    }
    LaunchedEffect(Store.int("wave_gain"), Store.int("wave_smooth"), Store.int("sync_offset"), Store.bool("reduced_fx")) {
        V.gain = Store.int("wave_gain") / 100f
        V.smooth = Store.int("wave_smooth") / 100f
        V.offsetMs = Store.int("sync_offset")
        V.reduced = Store.bool("reduced_fx")
    }
    LaunchedEffect(cur?.key) { V.seed = ((cur?.key ?: "").hashCode() and 0x7fffffff) % 10000 + 1; m.refreshVisualEq() }

    // ---- frame loop (only while the app is on screen) ----------------------------------------------
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) withFrameNanos { V.tick(it) }
        }
    }

    // ---- start page ------------------------------------------------------------------------------
    LaunchedEffect(Unit) { if (Store.str("start_page") == "online") m.page = "online" }

    BackHandler(enabled = m.anyDialog() || m.eqRequest != null || m.settingsPage.isNotEmpty() || m.page != "home") {
        when {
            m.menu != null -> m.menu = null
            m.prompt != null -> m.prompt = null
            m.confirm != null -> m.confirm = null
            m.notice != null -> m.notice = null
            m.eqRequest != null -> m.eqRequest = null
            m.page == "settings" && m.settingsPage.isNotEmpty() -> m.settingsPage = ""
            m.page == "now" -> m.page = m.lastPage
            else -> m.page = "home"
        }
    }

    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.firstOrNull()?.let { Touch.x = it.position.x; Touch.y = it.position.y; Touch.stamp = System.nanoTime() }
            }
        }
    }) {
        Backdrop()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
            Box(Modifier.weight(1f)) {
                Crossfade(m.page, animationSpec = tween(160), label = "page") { p ->
                    when (p) {
                        "online" -> OnlinePage(m)
                        "collection" -> CollectionPage(m)
                        "now" -> NowPlayingPage(m)
                        "settings" -> SettingsRoot(m)
                        else -> HomePage(m)
                    }
                }
            }
            if (m.page != "now") { Dock(m); Spacer(Modifier.height(6.dp)) }
            BottomBar(m)
        }
        m.eqRequest?.let { r -> EqualizerScreen(m, r) { m.eqRequest = null } }
        m.menu?.let { MenuSheet(it) { m.menu = null } }
        m.prompt?.let { PromptDialog(it) { m.prompt = null } }
        m.confirm?.let { ConfirmDialog(it) { m.confirm = null } }
        m.notice?.let { NoticeDialog(it) { m.notice = null } }
        Toasts(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 150.dp))
    }
}

@Composable
private fun Toasts(modifier: Modifier) {
    val msg = Engine.message
    var text by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(msg) {
        if (msg != null) {
            text = msg; visible = true
            delay(if (Engine.messageError) 3800L else 1800L)
            visible = false
            if (Engine.message == msg) Engine.message = null
        }
    }
    Box(modifier) { ToastPill(text, visible) }
}

@Composable
private fun BottomBar(m: AppModel) {
    val shape = RoundedCornerShape(26.dp)
    val items = listOf(Triple("home", "Home", "home"), Triple("online", "Online", "globe"), Triple("collection", "Library", "library"),
        Triple("now", "Playing", "disc"), Triple("settings", "Settings", "settings"))
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(bottom = 8.dp).clip(shape).background(cardFill).border(1.dp, cardEdge, shape).padding(4.dp)) {
        for ((k, label, icon) in items) {
            val sel = m.page == k
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (sel) T.c1.a(0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(remember { MutableInteractionSource() }, null) { m.go(k) }.padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Ico(icon, 22, if (sel) accentLight else ink(0.62f))
                Txt(label, 10, if (sel) FontWeight.Bold else FontWeight.Medium, if (sel) accentLight else ink(0.62f), 1)
            }
        }
    }
}
