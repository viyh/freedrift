package io.github.viyh.freedrift

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.viyh.freedrift.audio.LastSessionRef
import io.github.viyh.freedrift.audio.Scene
import io.github.viyh.freedrift.audio.SceneRepository
import io.github.viyh.freedrift.audio.PlaybackService
import io.github.viyh.freedrift.audio.PlaybackState
import io.github.viyh.freedrift.audio.Playlist
import io.github.viyh.freedrift.audio.PlaylistRepository
import io.github.viyh.freedrift.audio.AppSettings
import io.github.viyh.freedrift.audio.SoundLibrary
import io.github.viyh.freedrift.audio.SoundSettings
import io.github.viyh.freedrift.audio.SoundSettingsRepository
import io.github.viyh.freedrift.audio.StarterScenes
import io.github.viyh.freedrift.audio.SoundSource
import io.github.viyh.freedrift.ui.HomeScreen
import io.github.viyh.freedrift.ui.SceneEditorScreen
import io.github.viyh.freedrift.ui.PlaylistEditorScreen
import io.github.viyh.freedrift.ui.Tab
import io.github.viyh.freedrift.ui.theme.FreeDriftTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var service: PlaybackService? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _sounds = MutableStateFlow<List<SoundSource>>(emptyList())
    val sounds: StateFlow<List<SoundSource>> = _sounds.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _scenes = MutableStateFlow<List<Scene>>(emptyList())
    val scenes: StateFlow<List<Scene>> = _scenes.asStateFlow()

    private val _crossfadeSec = MutableStateFlow(0)
    val crossfadeSec: StateFlow<Int> = _crossfadeSec.asStateFlow()

    private val _fadeInSec = MutableStateFlow(0)
    val fadeInSec: StateFlow<Int> = _fadeInSec.asStateFlow()

    private val _timerFadeOutSec = MutableStateFlow(0)
    val timerFadeOutSec: StateFlow<Int> = _timerFadeOutSec.asStateFlow()

    private val _soundSettings = MutableStateFlow<Map<String, SoundSettings>>(emptyMap())
    val soundSettings: StateFlow<Map<String, SoundSettings>> = _soundSettings.asStateFlow()

    private val _batteryExempt = MutableStateFlow(false)
    val batteryExempt: StateFlow<Boolean> = _batteryExempt.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val s = (binder as PlaybackService.LocalBinder).service()
            service = s
            lifecycleScope.launch {
                s.state.collect { _state.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reloadSounds()
        StarterScenes.seedIfNeeded(this)
        reloadPlaylists()
        reloadScenes()
        _crossfadeSec.value = AppSettings.crossfadeSeconds(this)
        _fadeInSec.value = AppSettings.fadeInSeconds(this)
        _timerFadeOutSec.value = AppSettings.timerFadeOutSeconds(this)
        _soundSettings.value = SoundSettingsRepository.all(this)

        setContent {
            FreeDriftTheme {
                val st by state.collectAsState()
                val snd by sounds.collectAsState()
                val pls by playlists.collectAsState()
                val scn by scenes.collectAsState()

                // Simple state-based navigation. Tab state lives here so editor round-trips preserve it.
                var editor: EditorTarget? by remember { mutableStateOf(null) }
                var tab: Tab by remember { mutableStateOf(Tab.SOUNDS) }
                val tabHistory = remember { mutableStateListOf<Tab>() }

                val smartDefaultTab: Tab = when {
                    st.sceneSession != null -> Tab.SCENES
                    st.playlistSession != null -> Tab.PLAYLISTS
                    st.current != null -> Tab.SOUNDS
                    st.lastSession?.kind == LastSessionRef.Kind.SCENE -> Tab.SCENES
                    st.lastSession?.kind == LastSessionRef.Kind.PLAYLIST -> Tab.PLAYLISTS
                    st.lastSession?.kind == LastSessionRef.Kind.SOUND -> Tab.SOUNDS
                    else -> Tab.SOUNDS
                }

                when (val e = editor) {
                    is EditorTarget.PlaylistEdit -> PlaylistEditorScreen(
                        initial = e.playlist,
                        availableSounds = snd,
                        availableScenes = scn,
                        onSave = { p ->
                            PlaylistRepository.upsert(this, p)
                            reloadPlaylists()
                            editor = null
                        },
                        onCancel = { editor = null },
                        onDelete = { id ->
                            PlaylistRepository.delete(this, id)
                            reloadPlaylists()
                            editor = null
                        },
                    )
                    is EditorTarget.SceneEdit -> SceneEditorScreen(
                        initial = e.scene,
                        availableSounds = snd,
                        onSave = { scene ->
                            SceneRepository.upsert(this, scene)
                            reloadScenes()
                            editor = null
                        },
                        onCancel = { editor = null },
                        onDelete = { id ->
                            SceneRepository.delete(this, id)
                            reloadScenes()
                            editor = null
                        },
                    )
                    null -> HomeScreen(
                        state = st,
                        sounds = snd,
                        playlists = pls,
                        scenes = scn,
                        crossfadeSeconds = crossfadeSec.collectAsState().value,
                        fadeInSeconds = fadeInSec.collectAsState().value,
                        timerFadeOutSeconds = timerFadeOutSec.collectAsState().value,
                        appVolume = st.appVolume,
                        duckOnNotifications = st.duckOnNotifications,
                        lastSessionName = resolveLastSessionName(st.lastSession, snd, pls, scn),
                        onResumeLastSession = { resumeLastSession(st.lastSession) },
                        onSetCrossfadeSeconds = { secs ->
                            service?.setCrossfadeSeconds(secs)
                            _crossfadeSec.value = secs
                        },
                        onSetFadeInSeconds = { secs ->
                            service?.setFadeInSeconds(secs)
                            _fadeInSec.value = secs
                        },
                        onSetTimerFadeOutSeconds = { secs ->
                            service?.setTimerFadeOutSeconds(secs)
                            _timerFadeOutSec.value = secs
                        },
                        onSetAppVolume = { v -> service?.setAppVolume(v) },
                        onSetDuckOnNotifications = { v -> service?.setDuckOnNotifications(v) },
                        onPlay = { source ->
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, PlaybackService::class.java)
                            )
                            service?.playSingle(source)
                        },
                        onPlayPlaylist = { playlist ->
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, PlaybackService::class.java)
                            )
                            service?.playPlaylist(playlist)
                        },
                        onEditPlaylist = { editor = EditorTarget.PlaylistEdit(it) },
                        onNewPlaylist = { editor = EditorTarget.PlaylistEdit(null) },
                        onPlayScene = { scene ->
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, PlaybackService::class.java)
                            )
                            service?.playScene(scene)
                        },
                        onEditScene = { editor = EditorTarget.SceneEdit(it) },
                        onNewScene = { editor = EditorTarget.SceneEdit(null) },
                        onSetLayerVolume = { idx, v -> service?.setLayerVolume(idx, v) },
                        onSaveSceneLevels = {
                            service?.saveSceneLevels()
                            reloadScenes()
                        },
                        onUpdateSceneDefaults = { scene ->
                            SceneRepository.upsert(this, scene)
                            reloadScenes()
                        },
                        onPause = { service?.pause() },
                        onStop = { service?.stop() },
                        onSetTimer = { d -> service?.setSleepTimer(d) },
                        onCancelTimer = { service?.cancelTimer() },
                        onPickSound = { pickSoundLauncher.launch(arrayOf("audio/*")) },
                        onRemoveUserSound = { u ->
                            SoundLibrary.removeUserSound(this, u)
                            reloadSounds()
                        },
                        soundSettings = soundSettings.collectAsState().value,
                        onSetSoundSettings = { soundId, s ->
                            SoundSettingsRepository.set(this, soundId, s)
                            _soundSettings.value = _soundSettings.value + (soundId to s)
                            service?.refreshSoundSettings()
                        },
                        onRequestBatteryExemption = ::requestBatteryExemption,
                        isBatteryExempt = batteryExempt.collectAsState().value,
                        tab = tab,
                        onTabChange = { newTab ->
                            if (tab != newTab) {
                                tabHistory.add(tab)
                                tab = newTab
                            }
                        },
                        canGoBack = tabHistory.isNotEmpty() || tab != smartDefaultTab,
                        onBack = {
                            if (tabHistory.isNotEmpty()) {
                                tab = tabHistory.removeAt(tabHistory.size - 1)
                            } else if (tab != smartDefaultTab) {
                                tab = smartDefaultTab
                            }
                        },
                    )
                }
            }
        }

        maybeRequestNotificationPermission()
    }

    private fun resolveLastSessionName(
        ref: LastSessionRef?,
        sounds: List<SoundSource>,
        playlists: List<Playlist>,
        scenes: List<Scene>,
    ): String? {
        if (ref == null) return null
        return when (ref.kind) {
            LastSessionRef.Kind.SOUND -> sounds.firstOrNull { it.id == ref.targetId }?.displayName
            LastSessionRef.Kind.PLAYLIST -> playlists.firstOrNull { it.id == ref.targetId }?.name
            LastSessionRef.Kind.SCENE -> scenes.firstOrNull { it.id == ref.targetId }?.name
        }
    }

    private fun resumeLastSession(ref: LastSessionRef?) {
        if (ref == null) return
        ContextCompat.startForegroundService(
            this, Intent(this, PlaybackService::class.java)
        )
        when (ref.kind) {
            LastSessionRef.Kind.SOUND -> {
                val src = (_sounds.value).firstOrNull { it.id == ref.targetId }
                src?.let { service?.playSingle(it) }
            }
            LastSessionRef.Kind.PLAYLIST -> {
                _playlists.value.firstOrNull { it.id == ref.targetId }
                    ?.let { service?.playPlaylist(it) }
            }
            LastSessionRef.Kind.SCENE -> {
                _scenes.value.firstOrNull { it.id == ref.targetId }
                    ?.let { service?.playScene(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val i = Intent(this, PlaybackService::class.java)
        bindService(i, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        // Re-poll the battery-optimization flag so the tip + settings card
        // hide immediately after the user grants the exemption from the
        // system dialog.
        _batteryExempt.value = isBatteryExempt()
    }

    override fun onStop() {
        super.onStop()
        runCatching { unbindService(connection) }
        service = null
    }

    private val requestNotifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isBatteryExempt(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressWarnings("BatteryLife")
    private fun requestBatteryExemption() {
        if (isBatteryExempt()) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }

    private val pickSoundLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Sound"
        SoundLibrary.addUserSound(this, uri, name)
        reloadSounds()
    }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                val raw = c.getString(idx) ?: return@use null
                raw.substringBeforeLast('.').ifBlank { raw }
            } else null
        }
    }

    private fun reloadSounds() {
        _sounds.value = SoundLibrary.all(this)
    }

    private fun reloadPlaylists() {
        _playlists.value = PlaylistRepository.load(this)
    }

    private fun reloadScenes() {
        _scenes.value = SceneRepository.load(this)
    }
}

private sealed interface EditorTarget {
    data class PlaylistEdit(val playlist: Playlist?) : EditorTarget
    data class SceneEdit(val scene: Scene?) : EditorTarget
}
