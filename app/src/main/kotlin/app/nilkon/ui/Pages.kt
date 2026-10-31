package app.nilkon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.nilkon.*
import app.nilkon.data.Song
import app.nilkon.data.Store
import app.nilkon.playback.Engine
import java.util.Calendar

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (h in 5..11) "GOOD MORNING" else if (h in 12..17) "GOOD AFTERNOON" else if (h < 23) "GOOD EVENING" else "LATE NIGHT"
}

// ---------------------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------------------

@Composable
fun HomePage(m: AppModel) {
    m.rev
    LaunchedEffect(m.library.size) { if (m.suggestions.none { s -> m.library.any { it.key == s.key } }) m.refreshSuggestions(false) }
    val recent = remember(m.rev, m.library) { Store.history.toList().asReversed().mapNotNull { m.songByKey(it) }.take(12) }
    val suggest = m.validSuggestions()
    val cur = Engine.current
    val playing = cur != null && Engine.isPlaying
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatLogo(56.dp)
            WordMark(40.dp)
        }
        // hero with live waves
        val shape = RoundedCornerShape(24.dp)
        Box(Modifier.fillMaxWidth().height(206.dp).clip(shape).background(cardFill.a(0.6f)).border(1.dp, cardEdge, shape)) {
            WaveField(Modifier.fillMaxSize(), Store.str("wave_style"))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to surface(0.62f), 0.62f to surface(0.34f), 1f to surface(0f))))
            Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp)) {
                Eyebrow(if (playing) "NOW PLAYING" else greeting())
                Txt(if (playing) cur!!.title else "Stay in your own frequency.", 24, FontWeight.ExtraBold, ink(0.98f), 2)
                val n = m.library.size
                val pls = Store.playlists.size
                Txt(if (playing) cur!!.artist else if (n > 0) "$n song${if (n != 1) "s" else ""} · $pls playlist${if (pls != 1) "s" else ""}" else "Add some music to get started.",
                    13, FontWeight.Normal, ink(0.7f), 1)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GButton("Play my mix", "play", "primary", iconSize = 16) { if (m.library.isEmpty()) m.pickFiles() else m.playMix() }
                    GButton("Shuffle all", "shuffle", "glass", iconSize = 16) { if (m.library.isEmpty()) m.pickFiles() else m.shuffleAll() }
                }
            }
        }
        CardSection("Recently played", "Pick up where you left off", null) {
            if (recent.isEmpty()) Placeholder("Songs you play will show up here.")
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recent, key = { it.key }) { s -> SongCard(m, s) { m.playFrom(recent, s) } }
            }
        }
        CardSection("Suggested for you", "A mix leaning towards the songs you play the most",
            { GButton("Refresh", "shuffle", "ghost", iconSize = 15) { m.refreshSuggestions() } }) {
            if (suggest.isEmpty()) Placeholder("Add music and your favourites will be suggested here.")
            else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(suggest, key = { it.key }) { s -> SongCard(m, s) { m.playFrom(suggest, s) } }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) { Txt(text, 13, FontWeight.Normal, textSub, 2, align = TextAlign.Center) }
}

@Composable
private fun CardSection(title: String, sub: String, action: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Txt(title, 18, FontWeight.ExtraBold); Txt(sub, 12, FontWeight.Normal, textSub, 2) }
            action?.invoke()
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------------------
// Online
// ---------------------------------------------------------------------------------------------------------

@Composable
fun OnlinePage(m: AppModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Txt("Online", 24, FontWeight.ExtraBold)
            Txt(if (m.onlineState == "loading") "Searching…" else "Search any song · stream instantly · download to listen offline", 12, FontWeight.Normal, textSub, 2)
        }
        GlassField(m.onlineQuery, { m.setOnlineQuery(it) }, "Search songs, or paste a song link…", Modifier.fillMaxWidth(), "search", { m.searchNow() })
        when (m.onlineState) {
            "done" -> LazyColumn(Modifier.fillMaxSize()) {
                items(m.onlineResults, key = { it.key }) { s -> OnlineRow(m, s) { m.playOnline(s) } }
                item { Spacer(Modifier.height(12.dp)) }
            }
            else -> {
                val (icon, title, desc) = when (m.onlineState) {
                    "loading" -> Triple("clock", "Searching…", "Looking for the best matches…")
                    "error" -> Triple("info", "Couldn't search", m.onlineError)
                    "empty" -> Triple("search", "No songs found", "Check the spelling or try the artist name.")
                    else -> Triple("globe", "Find any song", "Start typing a song or artist name in the search bar - results appear as you type.\nPress ▶ to play. The player keeps playing similar songs after it.")
                }
                Column(Modifier.fillMaxSize().padding(top = 40.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ico(icon, 44, ink(0.4f))
                    Txt(title, 18, FontWeight.ExtraBold, textMain, 1, align = TextAlign.Center)
                    Txt(desc, 13, FontWeight.Normal, textSub, 6, align = TextAlign.Center)
                    if (m.onlineState == "error") GButton("Try again", "reset", "primary", iconSize = 16) { m.searchNow() }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------
// Collection (All tracks, Liked, Recently played, playlists)
// ---------------------------------------------------------------------------------------------------------

@Composable
fun CollectionPage(m: AppModel) {
    m.rev
    val name = m.collection
    if (name.startsWith(PL_PREFIX) && !Store.playlists.containsKey(name.removePrefix(PL_PREFIX))) { m.collection = ALL; return }
    val songs = remember(m.rev, m.collection, m.collectionQuery, m.library) { m.visibleSongs() }
    val searching = m.collectionQuery.isNotBlank()
    val isPl = name.startsWith(PL_PREFIX)
    val plName = name.removePrefix(PL_PREFIX)
    val countText = remember(songs, searching) {
        var t = "${songs.size} song${if (songs.size != 1) "s" else ""}"
        if (!searching) {
            val mins = songs.sumOf { it.durationMs } / 60000
            if (mins > 0) t += if (mins >= 60) " · ${mins / 60} h ${mins % 60} min" else " · $mins min"
        }
        t
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // collection chips
        Row(Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((k, label, icon) in listOf(Triple(ALL, "All tracks", "library"), Triple(LIKED, "Liked songs", "heart"), Triple(RECENT, "Recently played", "history")))
                GButton(label, icon, "chip", active = name == k, iconSize = 14) { m.showCollection(k) }
            for (p in Store.playlists.keys.toList()) {
                val key = PL_PREFIX + p
                Box(Modifier.pointerInput(p) { androidx.compose.foundation.gestures.detectTapGestures(onLongPress = { m.playlistMenu(p) }) }) {
                    GButton(p, "list-music", "chip", active = name == key, iconSize = 14) { m.showCollection(key) }
                }
            }
            GButton("", "plus", "chip", iconSize = 15) { m.createPlaylist() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(m.collectionTitle(name), 22, FontWeight.ExtraBold)
                Txt(countText, 12, FontWeight.Normal, textSub)
            }
            if (isPl) IconButton("more", 40, 20, ink(0.8f)) { m.playlistMenu(plName) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GButton("Play", "play", "primary", enabled = songs.isNotEmpty(), iconSize = 16) { m.playCollection(false) }
            GButton("Shuffle", "shuffle", "glass", enabled = songs.isNotEmpty(), iconSize = 16) { m.playCollection(true) }
            Spacer(Modifier.weight(1f))
            GButton("", "plus", "round", iconSize = 18) { m.pickFiles() }
            GButton("", "folder-plus", "round", iconSize = 18) { m.pickFolder() }
        }
        GlassField(m.collectionQuery, { m.collectionQuery = it }, "Search songs, artists, albums…", Modifier.fillMaxWidth())
        if (songs.isEmpty()) {
            val (t, d) = when {
                m.library.isEmpty() && name == ALL -> if (!m.hasAudioPermission) "Allow access to your music" to "NILKON needs permission to read the music on this phone. You can also add files or a folder with the buttons above."
                    else "Your library is empty" to "Add audio files or a folder with the buttons above."
                searching -> "No matches" to "Nothing in this collection matches your search."
                name == LIKED -> "No favourites yet" to "Tap the heart next to a song to keep it here."
                name == RECENT -> "Nothing played yet" to "The songs you play will appear here."
                isPl -> "This playlist is empty" to "Open the ⋯ menu on any song and choose “Add to playlist”."
                else -> "Nothing here" to ""
            }
            Column(Modifier.fillMaxSize().padding(top = 36.dp, start = 12.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Ico("music", 44, ink(0.4f))
                Txt(t, 18, FontWeight.ExtraBold, textMain, 2, align = TextAlign.Center)
                Txt(d, 13, FontWeight.Normal, textSub, 6, align = TextAlign.Center)
                if (m.library.isEmpty() && name == ALL) {
                    if (!m.hasAudioPermission) GButton("Allow access", "check", "primary", iconSize = 16) { m.requestAudioAccess() }
                    else GButton("Add music", "plus", "primary", iconSize = 17) { m.pickFiles() }
                }
            }
        } else {
            val canDrag = isPl && !searching
            var dragKey by remember { mutableStateOf<String?>(null) }
            var dragY by remember { mutableFloatStateOf(0f) }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(songs, key = { _, s -> s.key }) { i, s ->
                    val dragging = dragKey == s.key
                    Box(Modifier.zIndex(if (dragging) 1f else 0f).graphicsLayer { translationY = if (dragging) dragY else 0f; alpha = if (dragging) 0.92f else 1f }
                        .then(if (canDrag) Modifier.pointerInput(s.key, plName) {
                            val rowH = ROW_H.dp.toPx()
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragKey = s.key; dragY = 0f },
                                onDrag = { c, d ->
                                    c.consume(); dragY += d.y
                                    val list = Store.playlists[plName]
                                    val cur = list?.indexOf(s.key) ?: -1
                                    if (list != null && cur >= 0) {
                                        if (dragY > rowH * 0.55f && cur < list.size - 1) { Store.movePlaylistItem(plName, cur, cur + 1); m.changed(); dragY -= rowH }
                                        else if (dragY < -rowH * 0.55f && cur > 0) { Store.movePlaylistItem(plName, cur, cur - 1); m.changed(); dragY += rowH }
                                    }
                                },
                                onDragEnd = { dragKey = null; dragY = 0f },
                                onDragCancel = { dragKey = null; dragY = 0f })
                        } else Modifier)) {
                        TrackRow(m, s, i, longPressMenu = !canDrag, showGrip = canDrag && dragging) { m.playFrom(songs, s) }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier = this.then(Modifier.horizontalScroll(rememberScrollState()))
