package app.nilkon.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------------------------------------
// Specs: the app model holds one of each, the DialogHost draws them
// ---------------------------------------------------------------------------------------------------------

class MenuItem(val icon: String, val label: String, val enabled: Boolean = true, val danger: Boolean = false,
               val checked: Boolean = false, val onClick: () -> Unit = {}) {
    val divider get() = label.isEmpty()
    companion object { fun divider() = MenuItem("", "") }
}

class MenuSpec(val title: String, val subtitle: String = "", val items: List<MenuItem>)

class PromptSpec(val title: String, val text: String, val placeholder: String = "", val initial: String = "",
                 val okText: String = "OK", val icon: String = "edit", val allowEmpty: Boolean = false,
                 val onOk: (String) -> Unit)

class ConfirmSpec(val title: String, val text: String, val okText: String = "OK", val danger: Boolean = false,
                  val icon: String = "info", val onOk: () -> Unit)

class NoticeSpec(val title: String, val text: String, val icon: String = "info")

// ---------------------------------------------------------------------------------------------------------
// Small shared pieces
// ---------------------------------------------------------------------------------------------------------

@Composable
fun Ico(name: String, size: Int = 18, color: Color = ink(0.9f), modifier: Modifier = Modifier) {
    Canvas(modifier.size(size.dp)) { drawIcon(name, 0f, 0f, this.size.width, color) }
}

/** Text input in the glass style (search boxes, dialogs). */
@Composable
fun GlassField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
               icon: String? = "search", onSubmit: (() -> Unit)? = null, autoFocus: Boolean = false, onClear: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    val focus = remember { FocusRequester() }
    var tf by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (tf.text != value) tf = TextFieldValue(value, TextRange(value.length))
    val keyboard = LocalSoftwareKeyboardController.current
    val fm = LocalFocusManager.current
    LaunchedEffect(autoFocus) { if (autoFocus) { runCatching { focus.requestFocus() } } }
    Row(modifier.height(44.dp).clip(shape).background(ink(0.06f)).border(1.dp, ink(0.13f), shape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Ico(icon, 18, ink(0.55f)); Spacer(Modifier.width(9.dp)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Txt(placeholder, 14, FontWeight.Normal, ink(0.40f))
            BasicTextField(tf, { tf = it; if (it.text != value) onChange(it.text) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                textStyle = TextStyle(color = textMain, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(T.c1),
                keyboardOptions = KeyboardOptions(imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Done),
                keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke(); keyboard?.hide(); fm.clearFocus() },
                    onDone = { onSubmit?.invoke(); keyboard?.hide(); fm.clearFocus() }))
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, null) {
                onChange(""); onClear?.invoke()
            }, contentAlignment = Alignment.Center) { Ico("x", 14, ink(0.65f)) }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Dialog frame
// ---------------------------------------------------------------------------------------------------------

@Composable
private fun Scrim(onDismiss: () -> Unit, align: Alignment, content: @Composable BoxScope.() -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(190), label = "scrim")
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * a))
        .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime)).padding(16.dp),
        contentAlignment = align) {
        Box(Modifier.graphicsLayer { alpha = a; val s = 0.96f + 0.04f * a; scaleX = s; scaleY = s; translationY = (1f - a) * 24f }
            .clickable(remember { MutableInteractionSource() }, null) {}, content = content)
    }
}

@Composable
private fun DialogCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.widthIn(max = 420.dp).fillMaxWidth().clip(shape).background(menuBg).border(1.dp, ink(0.16f), shape).padding(22.dp),
        content = content)
}

@Composable
private fun DialogHeader(icon: String, title: String, sub: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, 24, if (T.dark) lighter(T.c1, 115) else darker(T.c1, 115))
        Spacer(Modifier.width(12.dp))
        Column {
            Txt(title, 18, FontWeight.ExtraBold, textMain, 2)
            if (sub.isNotEmpty()) Txt(sub, 12, FontWeight.Normal, textSub, 2)
        }
    }
}

@Composable
fun PromptDialog(spec: PromptSpec, onClose: () -> Unit) {
    var text by remember { mutableStateOf(spec.initial) }
    fun ok() { val t = text.trim(); if (t.isEmpty() && !spec.allowEmpty) return; onClose(); spec.onOk(t) }
    Scrim(onClose, Alignment.Center) {
        DialogCard {
            DialogHeader(spec.icon, spec.title)
            if (spec.text.isNotEmpty()) { Spacer(Modifier.height(10.dp)); Txt(spec.text, 13, FontWeight.Normal, textSub, 6) }
            Spacer(Modifier.height(14.dp))
            GlassField(text, { text = it }, spec.placeholder, Modifier.fillMaxWidth(), null, { ok() }, autoFocus = true)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                GButton("Cancel", null, "glass") { onClose() }
                GButton(spec.okText, "check", "primary", enabled = spec.allowEmpty || text.isNotBlank()) { ok() }
            }
        }
    }
}

@Composable
fun ConfirmDialog(spec: ConfirmSpec, onClose: () -> Unit) {
    Scrim(onClose, Alignment.Center) {
        DialogCard {
            DialogHeader(spec.icon, spec.title)
            Spacer(Modifier.height(10.dp))
            Txt(spec.text, 13, FontWeight.Normal, textSub, 10)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                GButton("Cancel", null, "glass") { onClose() }
                GButton(spec.okText, if (spec.danger) "trash" else "check", if (spec.danger) "glass" else "primary") { onClose(); spec.onOk() }
            }
        }
    }
}

@Composable
fun NoticeDialog(spec: NoticeSpec, onClose: () -> Unit) {
    Scrim(onClose, Alignment.Center) {
        DialogCard {
            DialogHeader(spec.icon, spec.title)
            Spacer(Modifier.height(10.dp))
            Txt(spec.text, 13, FontWeight.Normal, textSub, 12)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { GButton("OK", "check", "primary") { onClose() } }
        }
    }
}

/** Action list, shown as a sheet at the bottom of the screen. */
@Composable
fun MenuSheet(spec: MenuSpec, onClose: () -> Unit) {
    Scrim(onClose, Alignment.BottomCenter) {
        val shape = RoundedCornerShape(22.dp)
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().heightIn(max = 560.dp).clip(shape).background(menuBg)
            .border(1.dp, ink(0.16f), shape).padding(vertical = 12.dp)) {
            if (spec.title.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Txt(spec.title, 15, FontWeight.ExtraBold, textMain, 2)
                    if (spec.subtitle.isNotEmpty()) Txt(spec.subtitle, 12, FontWeight.Normal, textSub, 1)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(ink(0.10f)))
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                spec.items.forEach { item ->
                    if (item.divider) {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(ink(0.10f)))
                    } else {
                        val col = if (item.danger) rgb(255, 120, 120) else ink(0.92f)
                        Row(Modifier.fillMaxWidth().alpha(if (item.enabled) 1f else 0.4f)
                            .clickable(remember { MutableInteractionSource() }, null, enabled = item.enabled) { onClose(); item.onClick() }
                            .padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Ico(item.icon, 18, col)
                            Spacer(Modifier.width(14.dp))
                            Txt(item.label, 14, FontWeight.SemiBold, col, 1, Modifier.weight(1f))
                            if (item.checked) Ico("check", 16, if (T.dark) lighter(T.c1, 120) else darker(T.c1, 115))
                        }
                    }
                }
            }
        }
    }
}
