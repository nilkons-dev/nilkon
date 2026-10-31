package app.nilkon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.nilkon.AppModel
import app.nilkon.data.Covers
import app.nilkon.data.Store
import app.nilkon.playback.Effects
import app.nilkon.playback.Engine

@Composable
fun Pill(text: String) {
    if (text.isEmpty()) return
    val shape = RoundedCornerShape(40.dp)
    Box(Modifier.clip(shape).background(ink(0.07f)).border(1.dp, ink(0.14f), shape).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Txt(text, 11, FontWeight.SemiBold, ink(0.72f))
    }
}

private fun analysisPill(): String {
    val s = Engine.current ?: return ""
    return when {
        Engine.waiting -> "Waiting for internet…"
        Engine.analysis != null -> if (Engine.envelope != null) "Live spectrum" else "Analysing audio…"
        Engine.liveSpectrum && Effects.canVisualize -> "Live spectrum"
        s.isOnline -> "Streaming"
        else -> "Simulated waves"
    }
}

private fun formatPill(m: AppModel): String {
    val s = Engine.current ?: return ""
    return if (s.isOnline && !m.isDownloaded(s.key)) "ONLINE · stream" else Engine.formatInfo
}

@Composable
fun Controls(scale: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy((10 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        CatButton("toggle", "shuffle", scale, Engine.shuffle) { Engine.setShuffle(!Engine.shuffle) }
        CatButton("prev", "prev", scale) { Engine.previous() }
        CatButton("play", if (Engine.isPlaying) "pause" else "play", scale) { Engine.toggle() }
        CatButton("next", "next", scale) { Engine.next() }
        CatButton("toggle", if (Engine.repeat == Player.REPEAT_MODE_ONE) "repeat-1" else "repeat", scale, Engine.repeat != Player.REPEAT_MODE_OFF) { Engine.cycleRepeat() }
    }
}

@Composable
fun SeekRow(modifier: Modifier = Modifier, barHeight: Int = 40) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Txt(fmtTime(Engine.positionMs), 11, FontWeight.Medium, ink(0.6f), 1, Modifier.width(38.dp), TextAlign.Start)
        SeekBar(Modifier.weight(1f).height(barHeight.dp), Engine.positionMs, Engine.durationMs, Engine.envelope, Engine.analysisKey, {}) { Engine.seek(it) }
        Txt(fmtTime(Engine.durationMs), 11, FontWeight.Medium, ink(0.6f), 1, Modifier.width(38.dp), TextAlign.End)
    }
}

@Composable
fun NowPlayingPage(m: AppModel) {
    val cur = Engine.current
    m.rev
    val cover = Covers.peek(cur, 400)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("NOW PLAYING", Modifier.weight(1f))
            val liked = cur != null && cur.key in Store.liked
            IconButton(if (liked) "heart-fill" else "heart", 40, 20, if (liked) heartColor else ink(0.7f)) { cur?.let { m.toggleLike(it) } }
            IconButton("more", 40, 20, ink(0.8f)) { cur?.let { m.songMenu(it) } }
        }
        Turntable(Modifier.widthIn(max = 400.dp).fillMaxWidth().aspectRatio(1f), cover)
        Spacer(Modifier.height(6.dp))
        Txt(cur?.title ?: "Nothing playing", 22, FontWeight.ExtraBold, textMain, 2, Modifier.fillMaxWidth(), TextAlign.Center)
        Txt(cur?.artist ?: "Add local music to get started.", 14, FontWeight.Normal, textSub, 1, Modifier.fillMaxWidth(), TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Pill(formatPill(m)); Pill(analysisPill()) }
        Spacer(Modifier.height(8.dp))
        SpectrumView(Modifier.fillMaxWidth().height(66.dp))
        Spacer(Modifier.height(4.dp))
        SeekRow()
        Spacer(Modifier.height(2.dp))
        Controls(1f)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val step = Store.int("seek_step")
            GButton("−$step s", null, "chip") { Engine.seekBy(-step) }
            GButton("+$step s", null, "chip") { Engine.seekBy(step) }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(if (Engine.muted || Engine.volume == 0) "volume-x" else if (Engine.volume < 40) "volume-1" else "volume-2", 40, 19,
                ink(0.8f)) { Engine.toggleMute() }
            GButton("−", null, "chip") { Engine.nudgeVolume(-Store.int("volume_step")) }
            GlassSlider(if (Engine.muted) 0 else Engine.volume, 0, 100, Modifier.weight(1f).height(32.dp).padding(horizontal = 6.dp)) { Engine.setVolume(it) }
            GButton("+", null, "chip") { Engine.nudgeVolume(Store.int("volume_step")) }
            Txt("${if (Engine.muted) 0 else Engine.volume}", 12, FontWeight.Medium, ink(0.6f), 1, Modifier.width(34.dp), TextAlign.End)
        }
        Spacer(Modifier.height(8.dp))
        GButton("Equalizer", "eq", "glass", Modifier.fillMaxWidth(), iconSize = 18) { m.openEq(cur, false) }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("UP NEXT", Modifier.weight(1f))
            if (m.sleepLabel().isNotEmpty()) Pill(m.sleepLabel())
        }
        Spacer(Modifier.height(4.dp))
        val q = Engine.queue
        val qi = Engine.queueIndex
        val upcoming = remember(q.size, qi) { (qi + 1 until q.size).toList() }
        if (upcoming.isEmpty()) Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
            Txt(if (cur == null) "Play something and what comes next shows up here." else "Nothing queued after this song.", 12, FontWeight.Normal, textSub, 2, align = TextAlign.Center)
        } else Column(Modifier.fillMaxWidth()) {
            for (idx in upcoming.take(60)) {
                val s = q.getOrNull(idx) ?: continue
                Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { Engine.jumpTo(idx) }.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Txt("${idx - qi}", 11, FontWeight.Normal, ink(0.45f), 1, Modifier.width(24.dp), TextAlign.Center)
                    Thumb(s, 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Txt(s.title, 13, FontWeight.SemiBold, ink(0.94f)); Txt(s.artist, 11, FontWeight.Normal, ink(0.55f)) }
                    IconButton("x", 34, 14, ink(0.55f)) { Engine.removeFromQueue(idx) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Compact player shown above the bottom bar on every page except Now Playing. */
@Composable
fun Dock(m: AppModel) {
    val cur = Engine.current
    val cover = Covers.peek(cur, 200)
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(shape).background(cardFill).border(1.dp, cardEdge, shape).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(remember { MutableInteractionSource() }, null) { m.go("now") },
                verticalAlignment = Alignment.CenterVertically) {
                MiniDisc(46.dp, cover)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Txt(cur?.title ?: "Choose a song", 14, FontWeight.Bold, textMain)
                    Txt(cur?.artist ?: "NILKON", 12, FontWeight.Normal, textSub)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CatButton("prev", "prev", 0.6f) { Engine.previous() }
                CatButton("play", if (Engine.isPlaying) "pause" else "play", 0.62f) { Engine.toggle() }
                CatButton("next", "next", 0.6f) { Engine.next() }
            }
        }
        SeekRow(barHeight = 30)
    }
}
