package app.nilkon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nilkon.AppModel
import app.nilkon.DlState
import app.nilkon.data.Song
import app.nilkon.data.Store
import app.nilkon.playback.Engine

const val ROW_H = 60

val accentLight: Color get() = if (T.dark) lighter(T.c1, 130) else darker(T.c1, 120)
val heartColor: Color get() = hsvColor(0.96f, 0.55f, 1f)

@Composable
fun IconButton(icon: String, size: Int = 36, iconSize: Int = 18, color: Color = ink(0.7f), onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center) { Ico(icon, iconSize, color) }
}

/** One song in a list: number / now-playing bars, cover, title, artist, favourite, length and the ⋯ menu. */
@Composable
fun TrackRow(m: AppModel, song: Song, index: Int, modifier: Modifier = Modifier, longPressMenu: Boolean = true,
             showGrip: Boolean = false, reorder: Reorder? = null, onPlay: () -> Unit) {
    val isCur = Engine.isCurrent(song.key)
    val playingNow = isCur && Engine.isPlaying
    m.rev
    val liked = song.key in Store.liked
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.fillMaxWidth().height(ROW_H.dp).padding(horizontal = 4.dp, vertical = 3.dp).clip(shape)
        .then(if (isCur) Modifier.background(T.c1.a(0.16f)).border(1.dp, T.c1.a(0.42f), shape) else Modifier)
        .pointerInput(song.key, longPressMenu) {
            detectTapGestures(onTap = { onPlay() }, onLongPress = { if (longPressMenu) m.songMenu(song) })
        }.padding(start = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            if (isCur) {
                Canvas(Modifier.size(28.dp)) {
                    drawCircle(T.c1.a(0.30f))
                    V.frame
                    drawMiniBars(size.width / 2f - 7.dp.toPx(), size.height / 2f - 7.dp.toPx(), 14.dp.toPx(), 14.dp.toPx(), accentLight, playingNow, 3)
                }
            } else if (showGrip) {
                Ico("grip", 16, ink(0.45f))
            } else {
                Txt((index + 1).toString(), 12, FontWeight.Normal, ink(0.46f), 1, align = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.width(8.dp))
        Thumb(song, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(song.title, 14, FontWeight.SemiBold, if (isCur) accentLight else ink(0.96f))
            Txt(song.artist, 12, FontWeight.Normal, ink(0.55f))
        }
        Box(Modifier.size(38.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, null) { m.toggleLike(song) },
            contentAlignment = Alignment.Center) { Ico(if (liked) "heart-fill" else "heart", 19, if (liked) heartColor else ink(0.45f)) }
        if (reorder == null) {
            Txt(if (song.durationMs > 0) fmtTime(song.durationMs) else "—", 12, FontWeight.Normal, ink(0.58f), 1,
                Modifier.widthIn(min = 38.dp), androidx.compose.ui.text.style.TextAlign.Center)
            IconButton("more", 36, 20, ink(0.7f)) { m.songMenu(song) }
        } else {
            IconButton("more", 36, 20, ink(0.7f)) { m.songMenu(song) }
            // drag handle: hold and move up / down
            Box(Modifier.width(44.dp).fillMaxHeight().pointerInput(song.key) {
                detectDragGestures(onDragStart = { reorder.start() }, onDragEnd = { reorder.end() }, onDragCancel = { reorder.end() }) { change, d ->
                    change.consume(); reorder.drag(d.y)
                }
            }, contentAlignment = Alignment.Center) { Ico("grip", 20, ink(0.6f)) }
        }
    }
}

/** Callbacks of a row's drag handle. */
class Reorder(val start: () -> Unit, val drag: (Float) -> Unit, val end: () -> Unit)

/** Card used on the Home page. */
@Composable
fun SongCard(m: AppModel, song: Song, width: Dp = 148.dp, onPlay: () -> Unit) {
    val isCur = Engine.isCurrent(song.key)
    val playingNow = isCur && Engine.isPlaying
    val shape = RoundedCornerShape(20.dp)
    val inner = width - 20.dp
    Column(Modifier.width(width).clip(shape)
        .background(if (isCur) T.c1.a(0.14f) else ink(0.05f))
        .border(1.dp, if (isCur) T.c1.a(0.45f) else ink(0.08f), shape)
        .pointerInput(song.key) { detectTapGestures(onTap = { onPlay() }, onLongPress = { m.songMenu(song) }) }
        .padding(10.dp)) {
        Box {
            Thumb(song, inner)
            if (isCur) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(34.dp).clip(CircleShape).background(T.c1),
                contentAlignment = Alignment.Center) { Ico(if (playingNow) "pause" else "play", 16, T.onAccent) }
        }
        Spacer(Modifier.height(10.dp))
        Txt(song.title, 13, FontWeight.SemiBold, if (isCur) accentLight else ink(0.96f), 1, Modifier.padding(horizontal = 2.dp))
        Txt(song.artist, 11, FontWeight.Normal, ink(0.55f), 1, Modifier.padding(horizontal = 2.dp))
    }
}

/** Result row of the Online page: play, cover, title, length, download state and the ⋯ menu. */
@Composable
fun OnlineRow(m: AppModel, song: Song, onPlay: () -> Unit) {
    val isCur = Engine.isCurrent(song.key)
    val playingNow = isCur && Engine.isPlaying
    val loadingNow = isCur && (Engine.buffering || Engine.waiting) && !Engine.isPlaying
    val st: DlState? = m.dl[song.key]
    m.rev
    val done = m.isDownloaded(song.key)
    val shape = RoundedCornerShape(16.dp)
    val bad = hsvColor(0.98f, 0.55f, 1f)
    Row(Modifier.fillMaxWidth().height(ROW_H.dp).padding(horizontal = 4.dp, vertical = 3.dp).clip(shape)
        .then(if (isCur) Modifier.background(T.c1.a(0.16f)).border(1.dp, T.c1.a(0.42f), shape) else Modifier)
        .pointerInput(song.key) { detectTapGestures(onTap = { onPlay() }, onLongPress = { m.songMenu(song) }) }
        .padding(start = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(T.c1.a(if (isCur) 0.95f else 0.30f)), contentAlignment = Alignment.Center) {
            Ico(if (loadingNow) "clock" else if (playingNow) "pause" else "play", 15, if (isCur) T.onAccent else accentLight)
        }
        Spacer(Modifier.width(10.dp))
        Thumb(song, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(song.title, 14, FontWeight.SemiBold, if (isCur) accentLight else ink(0.96f))
            val (sub, col) = when {
                st?.state == "downloading" -> "Downloading… ${(st.pct * 100).toInt()}%" to T.c1
                st?.state == "queued" -> "Waiting to download…" to T.c1
                st?.state == "error" -> (st.error.ifEmpty { "Download failed - tap the arrow to retry" }) to bad
                done -> "In your library · ${song.artist}" to ink(0.6f)
                else -> song.artist.ifEmpty { "Unknown artist" } to ink(0.55f)
            }
            Txt(sub, 12, FontWeight.Normal, col)
        }
        Txt(if (song.durationMs > 0) fmtTime(song.durationMs) else "", 12, FontWeight.Normal, ink(0.58f), 1,
            Modifier.widthIn(min = 38.dp), androidx.compose.ui.text.style.TextAlign.Center)
        // download button
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, null) { m.requestDownload(song) },
            contentAlignment = Alignment.Center) {
            val busy = st?.state == "downloading" || st?.state == "queued"
            if (busy) {
                val pct = st?.pct ?: 0f
                Canvas(Modifier.size(28.dp)) {
                    val sw = 2.2.dp.toPx()
                    drawCircle(ink(0.16f), size.minDimension / 2f - sw / 2f, style = Stroke(sw))
                    drawArc(T.c1, -90f, 360f * pct.coerceAtLeast(0.02f), false, Offset(sw / 2f, sw / 2f),
                        Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                    drawIconCentered("x", size.width / 2f, size.height / 2f, 10.dp.toPx(), ink(0.8f))
                }
            } else if (done) Ico("check", 18, accentLight)
            else if (st?.state == "error") Ico("reset", 19, bad)
            else Ico("download", 19, ink(0.7f))
        }
        IconButton("more", 36, 20, ink(0.7f)) { m.songMenu(song) }
    }
}
