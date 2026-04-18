package io.github.viyh.freedrift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.viyh.freedrift.audio.Scene
import io.github.viyh.freedrift.audio.PlaybackState
import io.github.viyh.freedrift.audio.Playlist
import io.github.viyh.freedrift.audio.SoundSettings
import io.github.viyh.freedrift.audio.SoundSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

enum class Tab(val label: String, val subtitle: String) {
    SOUNDS("Sounds", "Individual sounds and loops"),
    SCENES("Scenes", "Layered soundscapes"),
    PLAYLISTS("Playlists", "Sequences of sounds and scenes"),
    SETTINGS("Settings", "Preferences and info"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: PlaybackState,
    sounds: List<SoundSource>,
    playlists: List<Playlist>,
    scenes: List<Scene>,
    crossfadeSeconds: Int,
    fadeInSeconds: Int,
    timerFadeOutSeconds: Int,
    appVolume: Float,
    duckOnNotifications: Boolean,
    onSetCrossfadeSeconds: (Int) -> Unit,
    onSetFadeInSeconds: (Int) -> Unit,
    onSetTimerFadeOutSeconds: (Int) -> Unit,
    onSetAppVolume: (Float) -> Unit,
    onSetDuckOnNotifications: (Boolean) -> Unit,
    onResumeLastSession: () -> Unit,
    lastSessionName: String?,
    onPlay: (SoundSource) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onEditPlaylist: (Playlist) -> Unit,
    onNewPlaylist: () -> Unit,
    onPlayScene: (Scene) -> Unit,
    onEditScene: (Scene) -> Unit,
    onNewScene: () -> Unit,
    onSetLayerVolume: (Int, Float) -> Unit,
    onSaveSceneLevels: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSetTimer: (Duration) -> Unit,
    onCancelTimer: () -> Unit,
    onPickSound: () -> Unit,
    onRemoveUserSound: (SoundSource.UserFile) -> Unit,
    soundSettings: Map<String, SoundSettings>,
    onSetSoundSettings: (String, SoundSettings) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    isBatteryExempt: Boolean,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    canGoBack: Boolean,
    onBack: () -> Unit,
) {
    var timerDialogOpen by remember { mutableStateOf(false) }
    var expandedPlayer by remember { mutableStateOf(false) }

    // When the expanded player is up, ITS BackHandler wins (more recently composed).
    // Always enabled — back is consumed even when there's nowhere to go, so the app
    // never closes via back gesture. Use swipe-up-from-bottom to close.
    BackHandler(enabled = !expandedPlayer) {
        if (canGoBack) onBack()
    }

    val hasPlayback = state.current != null || state.sceneSession != null
    val showMini = hasPlayback || (state.lastSession != null && lastSessionName != null)

    // Auto-close expanded player when nothing is playing anymore.
    if (expandedPlayer && !hasPlayback) expandedPlayer = false

    if (expandedPlayer) {
        ExpandedPlayer(
            state = state,
            appVolume = appVolume,
            onSetAppVolume = onSetAppVolume,
            onSetLayerVolume = onSetLayerVolume,
            onSaveSceneLevels = onSaveSceneLevels,
            onPause = onPause,
            onResume = { state.current?.let(onPlay) },
            onStop = onStop,
            onOpenTimer = { timerDialogOpen = true },
            onClose = { expandedPlayer = false },
        )
        if (timerDialogOpen) {
            TimerDialog(
                state = state,
                onSetTimer = { onSetTimer(it); timerDialogOpen = false },
                onCancelTimer = { onCancelTimer(); timerDialogOpen = false },
                onDismiss = { timerDialogOpen = false },
            )
        }
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(tab.label, fontWeight = FontWeight.Light)
                            Text(
                                tab.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        when (tab) {
                            Tab.SOUNDS -> IconButton(onClick = onPickSound) {
                                Icon(Icons.Default.Add, "Add sound", tint = MaterialTheme.colorScheme.primary)
                            }
                            Tab.SCENES -> IconButton(onClick = onNewScene) {
                                Icon(Icons.Default.Add, "New scene", tint = MaterialTheme.colorScheme.primary)
                            }
                            Tab.PLAYLISTS -> IconButton(onClick = onNewPlaylist) {
                                Icon(Icons.Default.Add, "New playlist", tint = MaterialTheme.colorScheme.primary)
                            }
                            Tab.SETTINGS -> {}
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        },
        bottomBar = {
            Column {
                if (showMini) {
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                    MiniPlayer(
                        state = state,
                        lastSessionName = lastSessionName,
                        onPause = onPause,
                        onResume = { state.current?.let(onPlay) },
                        onStop = onStop,
                        onResumeLast = onResumeLastSession,
                        onOpenTimer = { timerDialogOpen = true },
                        onExpand = { if (hasPlayback) expandedPlayer = true },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                NavigationBar(containerColor = Color.Black) {
                    Tab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { onTabChange(t) },
                            icon = { Icon(iconFor(t), contentDescription = t.label) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(pad)
        ) {
            when (tab) {
                Tab.SOUNDS -> SoundsTab(
                    sounds = sounds,
                    current = state.current,
                    onPlay = onPlay,
                    onRemoveUserSound = onRemoveUserSound,
                    soundSettings = soundSettings,
                    onSetSoundSettings = onSetSoundSettings,
                )
                Tab.SCENES -> ScenesTab(
                    scenes = scenes,
                    state = state,
                    onPlayScene = onPlayScene,
                    onEditScene = onEditScene,
                    onSetLayerVolume = onSetLayerVolume,
                    onSaveSceneLevels = onSaveSceneLevels,
                )
                Tab.PLAYLISTS -> PlaylistsTab(
                    playlists = playlists,
                    state = state,
                    onPlayPlaylist = onPlayPlaylist,
                    onEditPlaylist = onEditPlaylist,
                )
                Tab.SETTINGS -> SettingsTab(
                    crossfadeSeconds = crossfadeSeconds,
                    fadeInSeconds = fadeInSeconds,
                    timerFadeOutSeconds = timerFadeOutSeconds,
                    appVolume = appVolume,
                    duckOnNotifications = duckOnNotifications,
                    onSetCrossfadeSeconds = onSetCrossfadeSeconds,
                    onSetFadeInSeconds = onSetFadeInSeconds,
                    onSetTimerFadeOutSeconds = onSetTimerFadeOutSeconds,
                    onSetAppVolume = onSetAppVolume,
                    onSetDuckOnNotifications = onSetDuckOnNotifications,
                    isBatteryExempt = isBatteryExempt,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                )
            }
        }
    }

    if (timerDialogOpen) {
        TimerDialog(
            state = state,
            onSetTimer = {
                onSetTimer(it)
                timerDialogOpen = false
            },
            onCancelTimer = {
                onCancelTimer()
                timerDialogOpen = false
            },
            onDismiss = { timerDialogOpen = false },
        )
    }
}

private fun iconFor(t: Tab) = when (t) {
    Tab.SOUNDS -> Icons.Default.MusicNote
    Tab.SCENES -> Icons.Default.GraphicEq
    Tab.PLAYLISTS -> Icons.Default.LibraryMusic
    Tab.SETTINGS -> Icons.Default.Settings
}

// ---------- Mini player ----------

@Composable
private fun MiniPlayer(
    state: PlaybackState,
    lastSessionName: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onResumeLast: () -> Unit,
    onOpenTimer: () -> Unit,
    onExpand: () -> Unit,
) {
    val hasPlayback = state.current != null || state.sceneSession != null
    val title = when {
        state.sceneSession != null -> state.sceneSession.scene.name
        state.current != null -> state.current.displayName
        lastSessionName != null -> lastSessionName
        else -> ""
    }
    val subtitle = when {
        state.sceneSession != null -> "${state.sceneSession.scene.layers.size} layers"
        state.playlistSession != null ->
            "${state.playlistSession.playlist.name} · ${state.playlistSession.currentIndex + 1} of ${state.playlistSession.playlist.entries.size}"
        !hasPlayback && lastSessionName != null -> "Last played"
        else -> null
    }

    val swipeThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }
    val swipeUp = Modifier.pointerInput(hasPlayback) {
        if (!hasPlayback) return@pointerInput
        var dragTotal = 0f
        detectVerticalDragGestures(
            onDragStart = { dragTotal = 0f },
            onVerticalDrag = { _, dy -> dragTotal += dy },
            onDragEnd = { if (-dragTotal > swipeThresholdPx) onExpand() },
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = { if (hasPlayback) onExpand() },
        modifier = Modifier.fillMaxWidth().then(swipeUp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onOpenTimer) {
                    Icon(
                        Icons.Default.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (state.timerEndsAt != null)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = when {
                        state.isPlaying -> onPause
                        hasPlayback -> onResume
                        else -> onResumeLast
                    }
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (hasPlayback) {
                    IconButton(onClick = onStop) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // Sleep-timer progress thread
            if (state.timerEndsAt != null && state.timerTotalMs != null && state.timerTotalMs > 0) {
                var remainingMs by remember { mutableIntStateOf(0) }
                LaunchedEffect(state.timerEndsAt) {
                    val endsAt = state.timerEndsAt ?: return@LaunchedEffect
                    while (true) {
                        remainingMs = (endsAt - System.currentTimeMillis())
                            .coerceAtLeast(0L).toInt()
                        if (remainingMs == 0) break
                        delay(500)
                    }
                }
                val fraction = (remainingMs.toFloat() / state.timerTotalMs.toFloat())
                    .coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

// ---------- Timer dialog (opened from mini player) ----------

@Composable
private fun TimerDialog(
    state: PlaybackState,
    onSetTimer: (Duration) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val active = state.timerEndsAt != null
    var sliderMinutes by remember {
        mutableStateOf(
            if (active && state.timerTotalMs != null)
                (state.timerTotalMs / 60_000f).coerceAtLeast(1f)
            else 0f
        )
    }

    var remainingMs by remember { mutableStateOf(0L) }
    LaunchedEffect(state.timerEndsAt) {
        while (state.timerEndsAt != null) {
            remainingMs = (state.timerEndsAt - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1_000)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text(
                    when {
                        active -> "Remaining: ${formatMs(remainingMs)}"
                        sliderMinutes.roundToInt() == 0 -> "Off"
                        else -> formatMinutes(sliderMinutes.roundToInt())
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = sliderMinutes,
                    onValueChange = { sliderMinutes = it },
                    valueRange = 0f..720f,
                    enabled = !active,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    )
                )
                if (!active) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60, 90).forEach { m ->
                            TextButton(onClick = { onSetTimer(m.minutes) }) { Text("${m}m") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (active) {
                TextButton(onClick = onCancelTimer) { Text("Cancel timer") }
            } else {
                TextButton(
                    onClick = { onSetTimer(sliderMinutes.roundToInt().minutes) },
                    enabled = sliderMinutes.roundToInt() > 0,
                ) { Text("Start") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ---------- Sounds tab ----------

@Composable
private fun SoundsTab(
    sounds: List<SoundSource>,
    current: SoundSource?,
    onPlay: (SoundSource) -> Unit,
    onRemoveUserSound: (SoundSource.UserFile) -> Unit,
    soundSettings: Map<String, SoundSettings>,
    onSetSoundSettings: (String, SoundSettings) -> Unit,
) {
    var gapEditingFor: SoundSource? by remember { mutableStateOf(null) }

    if (sounds.isEmpty()) {
        EmptyState("No sounds yet.\nDrop .ogg files in app/src/main/assets/sounds/ or tap + to add one from your device.")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(sounds, key = { it.id }) { s ->
            val settings = soundSettings[s.id] ?: SoundSettings()
            Surface(
                onClick = { onPlay(s) },
                color = if (current?.id == s.id)
                    MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.displayName,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    ModeChip(
                        settings = settings,
                        onToggle = {
                            val next = if (settings.mode == SoundSettings.Mode.CONTINUOUS)
                                settings.copy(mode = SoundSettings.Mode.INTERMITTENT)
                            else settings.copy(mode = SoundSettings.Mode.CONTINUOUS)
                            onSetSoundSettings(s.id, next)
                        },
                        onLongPressIfIntermittent = { gapEditingFor = s },
                    )
                    if (s is SoundSource.UserFile) {
                        IconButton(onClick = { onRemoveUserSound(s) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    gapEditingFor?.let { s ->
        val current = soundSettings[s.id] ?: SoundSettings()
        GapSliderDialog(
            soundName = s.displayName,
            initial = current.minGapSec,
            onConfirm = { newMin ->
                onSetSoundSettings(s.id, current.copy(minGapSec = newMin))
                gapEditingFor = null
            },
            onDismiss = { gapEditingFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModeChip(
    settings: SoundSettings,
    onToggle: () -> Unit,
    onLongPressIfIntermittent: () -> Unit,
) {
    val intermittent = settings.mode == SoundSettings.Mode.INTERMITTENT
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(40.dp)
            .combinedClickable(
                onClick = onToggle,
                onLongClick = if (intermittent) onLongPressIfIntermittent else null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (intermittent) Icons.Default.Timelapse else Icons.Default.AllInclusive,
            contentDescription = if (intermittent)
                "Intermittent (long-press to adjust gap)"
            else "Continuous",
            tint = if (intermittent)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GapSliderDialog(
    soundName: String,
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var seconds by remember(initial) { mutableStateOf(initial.toFloat()) }
    val rounded = seconds.roundToInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(soundName) },
        text = {
            Column {
                Text(
                    "Minimum gap between plays: ${formatSeconds(rounded)}",
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Maximum is ${formatSeconds(rounded * 2)} (2× min).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = seconds,
                    onValueChange = { seconds = it },
                    valueRange = SoundSettings.MIN_GAP_MIN_SEC.toFloat()..SoundSettings.MIN_GAP_MAX_SEC.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(rounded) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatSeconds(s: Int): String {
    val m = s / 60
    val r = s % 60
    return when {
        m == 0 -> "${s}s"
        r == 0 -> "${m}m"
        else -> "${m}m ${r}s"
    }
}

// ---------- Scenes tab ----------

@Composable
private fun ScenesTab(
    scenes: List<Scene>,
    state: PlaybackState,
    onPlayScene: (Scene) -> Unit,
    onEditScene: (Scene) -> Unit,
    onSetLayerVolume: (Int, Float) -> Unit,
    onSaveSceneLevels: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.sceneSession?.let { session ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        session.scene.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(8.dp))
                    LayerSliderBank(
                        layers = session.scene.layers,
                        editable = false,
                        currentVolumes = session.currentVolumes,
                        onVolumeChange = onSetLayerVolume,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = onSaveSceneLevels) { Text("Save current levels as default") }
                    }
                }
            }
        }

        if (scenes.isEmpty()) {
            EmptyState("No scenes yet.\nA scene is a layered bundle of sounds — rain, wind, distant thunder, etc. — each with its own volume. Tap + to build one.")
            return
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            items(scenes, key = { it.id }) { scene ->
                val isActive = state.sceneSession?.scene?.id == scene.id
                Surface(
                    color = if (isActive) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(scene.name, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                "${scene.layers.size} layers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onEditScene(scene) }) {
                            Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onPlayScene(scene) }) {
                            Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Playlists tab ----------

@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    state: PlaybackState,
    onPlayPlaylist: (Playlist) -> Unit,
    onEditPlaylist: (Playlist) -> Unit,
) {
    if (playlists.isEmpty()) {
        EmptyState("No playlists yet.\nTap + to create one. Pick sounds, set how long each should play before fading into the next.")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        items(playlists, key = { it.id }) { p ->
            val isActive = state.playlistSession?.playlist?.id == p.id
            val currentIndex = state.playlistSession?.currentIndex
            Surface(
                color = if (isActive) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = MaterialTheme.colorScheme.onBackground)
                        val subtitle = buildString {
                            append("${p.entries.size} entries · ${p.totalMinutes} min total")
                            if (isActive && currentIndex != null) {
                                append(" · playing ${currentIndex + 1} of ${p.entries.size}")
                            }
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEditPlaylist(p) }) {
                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onPlayPlaylist(p) }) {
                        Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ---------- Settings tab ----------

@Composable
private fun SettingsTab(
    crossfadeSeconds: Int,
    fadeInSeconds: Int,
    timerFadeOutSeconds: Int,
    appVolume: Float,
    duckOnNotifications: Boolean,
    onSetCrossfadeSeconds: (Int) -> Unit,
    onSetFadeInSeconds: (Int) -> Unit,
    onSetTimerFadeOutSeconds: (Int) -> Unit,
    onSetAppVolume: (Float) -> Unit,
    onSetDuckOnNotifications: (Boolean) -> Unit,
    isBatteryExempt: Boolean,
    onRequestBatteryExemption: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!isBatteryExempt) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.BatteryAlert, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Battery optimization", color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "Grant exemption for uninterrupted overnight playback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onRequestBatteryExemption) { Text("Grant") }
                }
            }
        }

        // App volume
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row {
                    Text(
                        "Volume",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(appVolume * 100).roundToInt()}%", color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "App-only level, separate from the phone's media volume.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = appVolume,
                    onValueChange = onSetAppVolume,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    )
                )
            }
        }

        // Duck on notifications
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Duck during notifications",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "When a notification sound plays, lower the volume instead of pausing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = duckOnNotifications, onCheckedChange = onSetDuckOnNotifications)
            }
        }

        SecondsSettingCard(
            title = "Fade in",
            description = "How long it takes to ramp up to full volume when playback starts or resumes.",
            seconds = fadeInSeconds,
            maxSeconds = 30,
            onChange = onSetFadeInSeconds,
        )
        SecondsSettingCard(
            title = "Crossfade",
            description = "Overlap when a playlist advances to the next track.",
            seconds = crossfadeSeconds,
            maxSeconds = 30,
            onChange = onSetCrossfadeSeconds,
        )
        SecondsSettingCard(
            title = "Sleep-timer fade-out",
            description = "Gradual ramp to silence when the sleep timer ends.",
            seconds = timerFadeOutSeconds,
            maxSeconds = 120,
            onChange = onSetTimerFadeOutSeconds,
        )

        AboutCard()
    }
}

@Composable
private fun SecondsSettingCard(
    title: String,
    description: String,
    seconds: Int,
    maxSeconds: Int,
    onChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text("${seconds}s", color = MaterialTheme.colorScheme.primary)
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var v by remember(seconds) { mutableStateOf(seconds.toFloat()) }
            Slider(
                value = v,
                onValueChange = { v = it },
                onValueChangeFinished = { onChange(v.roundToInt()) },
                valueRange = 0f..maxSeconds.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    }
}

// ---------- Expanded now-playing ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedPlayer(
    state: PlaybackState,
    appVolume: Float,
    onSetAppVolume: (Float) -> Unit,
    onSetLayerVolume: (Int, Float) -> Unit,
    onSaveSceneLevels: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onOpenTimer: () -> Unit,
    onClose: () -> Unit,
) {
    val title = when {
        state.sceneSession != null -> state.sceneSession.scene.name
        else -> state.current?.displayName ?: ""
    }
    val subtitle = when {
        state.sceneSession != null -> "${state.sceneSession.scene.layers.size} layers"
        state.playlistSession != null ->
            "${state.playlistSession.playlist.name} · ${state.playlistSession.currentIndex + 1} of ${state.playlistSession.playlist.entries.size}"
        else -> null
    }

    // System back / edge-swipe-back: collapse the expanded view instead of minimizing app.
    BackHandler { onClose() }

    // Drag-down-to-dismiss on the top bar region.
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val dismissThresholdPx = with(LocalDensity.current) { 140.dp.toPx() }
    val dragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onVerticalDrag = { _, dy ->
                dragOffsetY = (dragOffsetY + dy).coerceAtLeast(0f)
            },
            onDragEnd = {
                if (dragOffsetY > dismissThresholdPx) {
                    onClose()
                } else {
                    scope.launch {
                        val anim = Animatable(dragOffsetY)
                        anim.animateTo(0f) { dragOffsetY = value }
                    }
                }
            },
            onDragCancel = {
                scope.launch {
                    val anim = Animatable(dragOffsetY)
                    anim.animateTo(0f) { dragOffsetY = value }
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.offset { IntOffset(0, dragOffsetY.toInt()) },
        containerColor = Color.Black,
        topBar = {
            Column {
                TopAppBar(
                    modifier = dragModifier,
                    title = { Text("Now playing", fontWeight = FontWeight.Light) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(pad),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Live layer sliders for scenes; no track-position bar for single sounds
                // (it would jump around with Continuous random-seek and look broken).
                if (state.sceneSession != null) {
                    LayerSliderBank(
                        layers = state.sceneSession.scene.layers,
                        editable = false,
                        currentVolumes = state.sceneSession.currentVolumes,
                        onVolumeChange = onSetLayerVolume,
                    )
                    TextButton(onClick = onSaveSceneLevels) { Text("Save current levels as default") }
                }
            }

            HorizontalDivider(
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Volume
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = appVolume,
                            onValueChange = onSetAppVolume,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(appVolume * 100).roundToInt()}%",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    // Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = onOpenTimer) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "Sleep timer",
                                tint = if (state.timerEndsAt != null)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = if (state.isPlaying) onPause else onResume) {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        IconButton(onClick = onStop) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.size(48.dp)) // visual balance for the timer icon
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatMinutes(m: Int): String {
    val h = m / 60
    val mm = m % 60
    return if (h > 0) "${h}h ${mm}m" else "${mm}m"
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
