package io.github.viyh.freedrift.audio

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import io.github.viyh.freedrift.MainActivity
import io.github.viyh.freedrift.R
import io.github.viyh.freedrift.FreeDriftApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PlaybackService : LifecycleService() {

    inner class LocalBinder : Binder() {
        fun service(): PlaybackService = this@PlaybackService
    }

    private val binder = LocalBinder()

    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer
    private lateinit var wrappedA: ForwardingPlayer
    private lateinit var wrappedB: ForwardingPlayer
    private var activeIsA = true
    private fun activePlayer(): ExoPlayer = if (activeIsA) playerA else playerB
    private fun idlePlayer(): ExoPlayer = if (activeIsA) playerB else playerA
    private fun activeWrapped(): ForwardingPlayer = if (activeIsA) wrappedA else wrappedB

    private var mediaSession: MediaSession? = null
    private var isForeground = false

    /** Active layer players, parallel to the current Mix's layers list. Empty when not in mix mode. */
    private val mixPlayers = mutableListOf<ExoPlayer>()

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: AudioFocusRequest? = null
    /** True when we paused playback because we lost focus; used to auto-resume on GAIN. */
    private var pausedByFocus = false
    /** True while we've ducked volume in response to CAN_DUCK. Restored on GAIN. */
    private var ducked = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Full loss: always pause.
                if (_state.value.isPlaying) {
                    pausedByFocus = true
                    pauseCurrentImmediate()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Notification-style interruption. User setting picks duck vs pause.
                if (duckOnNotifications) {
                    ducked = true
                    applyVolumeScaling()
                } else if (_state.value.isPlaying) {
                    pausedByFocus = true
                    pauseCurrentImmediate()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (ducked) {
                    ducked = false
                    applyVolumeScaling()
                }
                if (pausedByFocus && _state.value.current != null) {
                    pausedByFocus = false
                    resumeCurrentWithFade()
                }
            }
        }
    }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val fades = mutableMapOf<ExoPlayer, Job>()
    private var timerJob: Job? = null
    private var playlistJob: Job? = null

    /** Logical (pre-scaling) volume each player "wants" to be at. Actual player.volume = logical * appVolume * (duckFactor). */
    private val logicalVolumes = mutableMapOf<ExoPlayer, Float>()

    /** Per-player sound-settings snapshot (what soundId it's playing + the mode/gap for that sound). */
    private val soundSettingsForPlayer = mutableMapOf<ExoPlayer, Pair<String, SoundSettings>>()
    /** Pending intermittent-gap restart jobs. */
    private val intermittentJobs = mutableMapOf<ExoPlayer, Job>()
    private var appVolume: Float = 1f
    private var duckOnNotifications: Boolean = false

    var fadeInDuration: Duration = 1.seconds
    var crossfadeDuration: Duration = 8.seconds
    var timerFadeOutDuration: Duration = 30.seconds

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        // Load persisted settings.
        crossfadeDuration = AppSettings.crossfadeSeconds(this).seconds
        fadeInDuration = AppSettings.fadeInSeconds(this).seconds
        timerFadeOutDuration = AppSettings.timerFadeOutSeconds(this).seconds
        appVolume = AppSettings.appVolume(this)
        duckOnNotifications = AppSettings.duckOnNotifications(this)
        _state.update {
            it.copy(
                appVolume = appVolume,
                duckOnNotifications = duckOnNotifications,
                lastSession = AppSettings.lastSession(this),
            )
        }
        playerA = buildPlayer()
        playerB = buildPlayer()
        wrappedA = wrap(playerA)
        wrappedB = wrap(playerB)
        mediaSession = MediaSession.Builder(this, wrappedA).build()
    }

    private fun buildPlayer(handleFocus: Boolean = false): ExoPlayer = ExoPlayer.Builder(this)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            handleFocus,
        )
        .setHandleAudioBecomingNoisy(false)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (this@apply === activePlayer()) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                        refreshNotification()
                    }
                }
                override fun onPlaybackStateChanged(s: Int) {
                    val settings = soundSettingsForPlayer[this@apply]?.second ?: return
                    if (s == Player.STATE_ENDED &&
                        settings.mode == SoundSettings.Mode.INTERMITTENT) {
                        scheduleIntermittentRestart(this@apply, settings)
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error", error)
                }
            })
        }

    private fun wrap(p: ExoPlayer): ForwardingPlayer = object : ForwardingPlayer(p) {
        override fun play() { resumeActiveWithFade() }
        override fun pause() { pauseCurrentImmediate() }
        // Looping ambient audio has no meaningful "track progress" — hide the bar
        // on the lockscreen / notification.
        override fun getDuration(): Long = C.TIME_UNSET
        override fun getContentDuration(): Long = C.TIME_UNSET
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!activePlayer().playWhenReady || activePlayer().mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        fades.values.forEach { it.cancel() }
        fades.clear()
        intermittentJobs.values.forEach { it.cancel() }
        intermittentJobs.clear()
        soundSettingsForPlayer.clear()
        timerJob?.cancel()
        playlistJob?.cancel()
        mediaSession?.run {
            release()
        }
        mediaSession = null
        playerA.release()
        playerB.release()
        mixPlayers.forEach { runCatching { it.release() } }
        mixPlayers.clear()
        super.onDestroy()
    }

    // --- Public control ---

    fun playSingle(source: SoundSource) {
        Log.d(TAG, "playSingle(${source.id})")
        stopPlaylistScheduling()
        teardownMix()
        requestAudioFocus()
        _state.update { it.copy(playlistSession = null, mixSession = null) }
        playOnActive(source, fadeIn = true)
        promoteToForeground()
        rememberLastSession(LastSessionRef(LastSessionRef.Kind.SOUND, source.id))
    }

    fun playPlaylist(playlist: Playlist) {
        Log.d(TAG, "playPlaylist(${playlist.name}, ${playlist.entries.size} entries)")
        if (playlist.entries.isEmpty()) return
        stopPlaylistScheduling()
        teardownMix()
        requestAudioFocus()
        _state.update { it.copy(playlistSession = PlaylistSession(playlist, 0), mixSession = null) }

        startPlaylistEntry(playlist.entries[0], fadeIn = true)
        promoteToForeground()
        rememberLastSession(LastSessionRef(LastSessionRef.Kind.PLAYLIST, playlist.id))

        playlistJob = lifecycleScope.launch {
            var idx = 0
            while (isActive) {
                val entry = playlist.entries[idx]
                val entryMs = entry.durationMinutes * 60_000L
                val crossMs = crossfadeDuration.inWholeMilliseconds
                val waitMs = (entryMs - crossMs).coerceAtLeast(0)
                delay(waitMs)

                val nextIdx = (idx + 1) % playlist.entries.size
                val nextEntry = playlist.entries[nextIdx]
                val ok = transitionPlaylistEntry(nextEntry, nextIdx)
                if (!ok) {
                    Log.w(TAG, "Skipping missing entry ${nextEntry.soundId}")
                    idx = nextIdx
                    continue
                }
                delay(crossMs)
                idx = nextIdx
            }
        }
    }

    /** Start an entry immediately (no prior state assumed beyond playlistSession already set). */
    private fun startPlaylistEntry(entry: PlaylistEntry, fadeIn: Boolean) {
        if (entry.isMix) {
            val mix = MixRepository.load(this).firstOrNull { it.id == entry.mixId } ?: return
            startMixPool(mix, fadeDuration = if (fadeIn) fadeInDuration else MIN_FADE)
        } else {
            val source = resolveSoundSource(this, entry.soundId) ?: return
            playOnActive(source, fadeIn)
            _state.update { it.copy(mixSession = null) }
        }
    }

    /**
     * Returns false if the next entry can't be resolved. Dispatches based on whether
     * the transition is sound→sound (existing crossfadeTo) or involves a mix (parallel
     * fade-out of current audible players + fade-in of new ones over crossfadeDuration).
     */
    private fun transitionPlaylistEntry(next: PlaylistEntry, nextIdx: Int): Boolean {
        val prevWasMix = _state.value.mixSession != null
        val nextIsMix = next.isMix

        if (!prevWasMix && !nextIsMix) {
            val src = resolveSoundSource(this, next.soundId) ?: return false
            crossfadeTo(src, nextIdx)
            return true
        }

        // Resolve next
        val nextMix = if (nextIsMix) MixRepository.load(this).firstOrNull { it.id == next.mixId } else null
        val nextSource = if (!nextIsMix) resolveSoundSource(this, next.soundId) else null
        if (nextIsMix && nextMix == null) return false
        if (!nextIsMix && nextSource == null) return false

        val crossMs = crossfadeDuration

        // 1) Fade out whatever's currently audible.
        if (prevWasMix) {
            val outgoing = mixPlayers.toList()
            mixPlayers.clear() // logically "not current" anymore
            outgoing.forEach { p -> fade(p, p.volume, 0f, crossMs) }
            lifecycleScope.launch {
                delay(crossMs.inWholeMilliseconds)
                outgoing.forEach { p ->
                    runCatching {
                        p.playWhenReady = false
                        p.clearMediaItems()
                        p.release()
                    }
                }
                // Focus is now centralized (all modes), so we keep it held across transitions.
            }
        } else {
            val outgoing = activePlayer()
            fade(outgoing, outgoing.volume, 0f, crossMs)
            lifecycleScope.launch {
                delay(crossMs.inWholeMilliseconds)
                outgoing.playWhenReady = false
                outgoing.clearMediaItems()
            }
        }

        // 2) Set up incoming.
        if (nextIsMix) {
            startMixPool(nextMix!!, fadeDuration = crossMs)
            _state.update {
                it.copy(playlistSession = it.playlistSession?.copy(currentIndex = nextIdx))
            }
        } else {
            // Swap to the idle sound player and fade it in.
            activeIsA = !activeIsA
            val p = activePlayer()
            p.setMediaItem(buildMediaItem(nextSource!!))
            p.prepare()
            applySoundSettings(p, nextSource.id)
            setLogicalVolume(p, 0f)
            p.playWhenReady = true
            fade(p, 0f, 1f, crossMs)
            mediaSession?.setPlayer(activeWrapped())
            _state.update {
                it.copy(
                    current = nextSource,
                    mixSession = null,
                    playlistSession = it.playlistSession?.copy(currentIndex = nextIdx),
                )
            }
        }
        refreshNotification()
        return true
    }

    /**
     * Allocates a fresh pool of ExoPlayers for [mix] and fades each layer in to its
     * defaultVolume over [fadeDuration]. Updates mixSession/MediaSession state.
     * The caller is responsible for clearing any pre-existing mix pool first.
     */
    private fun startMixPool(mix: Mix, fadeDuration: Duration) {
        requestAudioFocus()

        // Pick the layer to bind the MediaSession to. Preference order:
        //   1. first Continuous layer (so lockscreen shows "playing" immediately — an
        //      intermittent layer is silent until its random initial delay fires)
        //   2. otherwise first layer (fallback — lockscreen will briefly show paused)
        val sessionLayerIdx = mix.layers.indexOfFirst { layer ->
            SoundSettingsRepository.get(this, layer.soundId).mode == SoundSettings.Mode.CONTINUOUS
        }.takeIf { it >= 0 } ?: 0

        var sessionPlayer: ExoPlayer? = null
        val newPlayers = mix.layers.mapIndexedNotNull { i, layer ->
            val src = resolveSoundSource(this, layer.soundId) ?: return@mapIndexedNotNull null
            val p = buildPlayer()
            val isPrimary = (i == sessionLayerIdx)
            p.setMediaItem(buildMediaItemForMix(src, mix, isPrimary = isPrimary))
            p.prepare()
            applySoundSettings(p, layer.soundId)
            setLogicalVolume(p, 0f)
            if (isPrimary) sessionPlayer = p
            p
        }
        mixPlayers.addAll(newPlayers)

        // Session binding uses the preferred (or fallback) player.
        (sessionPlayer ?: newPlayers.firstOrNull())?.let { sp ->
            val mixWrap = object : ForwardingPlayer(sp) {
                override fun play() { resumeCurrentWithFade() }
                override fun pause() { pauseCurrentImmediate() }
                override fun getDuration(): Long = C.TIME_UNSET
                override fun getContentDuration(): Long = C.TIME_UNSET
            }
            mediaSession?.setPlayer(mixWrap)
        }

        val primaryLayerSource = mix.layers.getOrNull(sessionLayerIdx)?.let {
            resolveSoundSource(this, it.soundId)
        }
        _state.update {
            it.copy(
                current = primaryLayerSource,
                isPlaying = true,
                mixSession = MixSession(
                    mix = mix,
                    currentVolumes = mix.layers.map { it.defaultVolume.coerceIn(0f, 1f) },
                ),
            )
        }
        refreshNotification()

        // Now start each player. Continuous layers play immediately; Intermittent layers
        // wait a random 0..2*minGap seconds before their first play so multiple
        // intermittent layers don't all fire at t=0.
        mix.layers.forEachIndexed { i, layer ->
            // Find the built player for this layer. mapIndexedNotNull may have skipped
            // layers with missing sources, so iterate by soundId lookup.
            val p = newPlayers.firstOrNull {
                it.currentMediaItem?.mediaId == layer.soundId
            } ?: return@forEachIndexed
            val targetVolume = layer.defaultVolume.coerceIn(0f, 1f)
            val settings = SoundSettingsRepository.get(this, layer.soundId)
            if (settings.mode == SoundSettings.Mode.INTERMITTENT) {
                scheduleIntermittentFirstPlay(p, settings, targetVolume, fadeDuration)
            } else {
                p.playWhenReady = true
                fade(p, 0f, targetVolume, fadeDuration)
            }
        }
    }

    /** Random 0..2*minGap delay before first audible play of an intermittent layer. */
    private fun scheduleIntermittentFirstPlay(
        p: ExoPlayer,
        settings: SoundSettings,
        targetVolume: Float,
        fadeDuration: Duration,
    ) {
        intermittentJobs[p]?.cancel()
        val maxDelayMs = (settings.minGapSec.toLong() * 2L * 1000L).coerceAtLeast(1L)
        val delayMs = Random.nextLong(0, maxDelayMs + 1)
        intermittentJobs[p] = lifecycleScope.launch {
            delay(delayMs)
            // Player may have been torn down while we waited.
            if (soundSettingsForPlayer[p] == null) return@launch
            // Respect user pause during the wait — resumeCurrentWithFade will restart
            // all mix players when they resume, including this one.
            if (!_state.value.isPlaying) return@launch
            p.seekTo(0)
            p.playWhenReady = true
            fade(p, 0f, targetVolume, fadeDuration)
        }
    }

    fun playMix(mix: Mix) {
        Log.d(TAG, "playMix(${mix.name}, ${mix.layers.size} layers)")
        if (mix.layers.isEmpty()) return
        stopPlaylistScheduling()
        // Stop single/playlist players.
        listOf(playerA, playerB).forEach { p ->
            fades[p]?.cancel()
            p.playWhenReady = false
            p.clearMediaItems()
        }
        teardownMix()
        requestAudioFocus()
        _state.update { it.copy(playlistSession = null) }
        startMixPool(mix, fadeDuration = fadeInDuration)
        refreshNotification()
        promoteToForeground()
        rememberLastSession(LastSessionRef(LastSessionRef.Kind.MIX, mix.id))
    }

    private fun rememberLastSession(ref: LastSessionRef) {
        AppSettings.setLastSession(this, ref)
        _state.update { it.copy(lastSession = ref) }
    }

    fun setLayerVolume(layerIndex: Int, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        val players = mixPlayers
        if (layerIndex !in players.indices) return
        // Direct set — no fade — so slider feels responsive.
        fades[players[layerIndex]]?.cancel()
        setLogicalVolume(players[layerIndex], v)
        _state.update {
            val ms = it.mixSession ?: return@update it
            it.copy(
                mixSession = ms.copy(
                    currentVolumes = ms.currentVolumes.toMutableList().also { v2 ->
                        if (layerIndex in v2.indices) v2[layerIndex] = v
                    }
                )
            )
        }
    }

    fun saveMixLevels() {
        val ms = _state.value.mixSession ?: return
        val updated = ms.mix.copy(
            layers = ms.mix.layers.mapIndexed { i, l ->
                l.copy(defaultVolume = ms.currentVolumes.getOrElse(i) { l.defaultVolume })
            }
        )
        MixRepository.upsert(this, updated)
        _state.update { it.copy(mixSession = ms.copy(mix = updated)) }
    }

    private fun teardownMix() {
        mixPlayers.forEach { p ->
            fades[p]?.cancel()
            fades.remove(p)
            intermittentJobs[p]?.cancel()
            intermittentJobs.remove(p)
            soundSettingsForPlayer.remove(p)
            logicalVolumes.remove(p)
            runCatching {
                p.playWhenReady = false
                p.clearMediaItems()
                p.release()
            }
        }
        mixPlayers.clear()
        // Restore MediaSession to playerA wrapper for single/playlist mode.
        mediaSession?.setPlayer(activeWrapped())
    }

    /** Request audio focus for any playback (all modes use central handling). Idempotent. */
    private fun requestAudioFocus(): Boolean {
        if (audioFocusRequest != null) return true
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        // willPauseWhenDucked=false lets the system send us CAN_DUCK events, which we
        // handle based on the user's "Duck during notifications" setting.
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .setWillPauseWhenDucked(false)
            .build()
        audioFocusRequest = req
        val granted = audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) Log.w(TAG, "audio focus not granted")
        return granted
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
        ducked = false
        pausedByFocus = false
    }

    // --- Logical-volume layer: all player.volume writes funnel through here so appVolume
    //     and the ducking factor apply uniformly. ---

    private fun volumeScale(): Float = appVolume * (if (ducked) 0.4f else 1f)

    private fun setLogicalVolume(p: ExoPlayer, v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        logicalVolumes[p] = clamped
        p.volume = clamped * volumeScale()
    }

    private fun applyVolumeScaling() {
        val scale = volumeScale()
        logicalVolumes.forEach { (p, lv) -> p.volume = lv * scale }
    }

    fun setAppVolume(v: Float) {
        appVolume = v.coerceIn(0f, 1f)
        AppSettings.setAppVolume(this, appVolume)
        applyVolumeScaling()
        _state.update { it.copy(appVolume = appVolume) }
    }

    fun setDuckOnNotifications(v: Boolean) {
        duckOnNotifications = v
        AppSettings.setDuckOnNotifications(this, v)
        _state.update { it.copy(duckOnNotifications = v) }
    }

    // --- Per-sound settings (Continuous / Intermittent) ---

    /**
     * Looks up the latest per-sound settings for [soundId] and applies them to [p]:
     * sets the right repeatMode, clears any pending intermittent-restart job, and resets
     * the "random-init" flag so we re-seek on next STATE_READY. Call this whenever we
     * put a new media item onto a player.
     */
    private fun applySoundSettings(p: ExoPlayer, soundId: String) {
        val settings = SoundSettingsRepository.get(this, soundId)
        soundSettingsForPlayer[p] = soundId to settings
        intermittentJobs[p]?.cancel()
        intermittentJobs.remove(p)
        p.repeatMode = when (settings.mode) {
            SoundSettings.Mode.CONTINUOUS -> Player.REPEAT_MODE_ONE
            SoundSettings.Mode.INTERMITTENT -> Player.REPEAT_MODE_OFF
        }
        // If user just switched a player to CONTINUOUS while it was ENDED (mid silent gap),
        // wake it back up immediately.
        if (settings.mode == SoundSettings.Mode.CONTINUOUS &&
            p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
        }
    }

    /** Re-read settings for every currently-playing sound. Called after the UI changes a toggle. */
    fun refreshSoundSettings() {
        soundSettingsForPlayer.keys.toList().forEach { p ->
            val soundId = soundSettingsForPlayer[p]?.first ?: return@forEach
            applySoundSettings(p, soundId)
        }
    }


    private fun scheduleIntermittentRestart(p: ExoPlayer, settings: SoundSettings) {
        intermittentJobs[p]?.cancel()
        val minMs = settings.minGapSec * 1000L
        val maxMs = minMs * 2L
        val gap = Random.nextLong(minMs, maxMs + 1)
        intermittentJobs[p] = lifecycleScope.launch {
            delay(gap)
            // Re-check settings at wake-time (user may have toggled to Continuous while we were sleeping).
            val currentMode = soundSettingsForPlayer[p]?.second?.mode
            if (currentMode == SoundSettings.Mode.INTERMITTENT) {
                p.seekTo(0)
                // If user paused during the gap, player stays paused at 0; resume makes it audible.
            }
        }
    }

    fun stop() {
        fades.values.forEach { it.cancel() }
        fades.clear()
        intermittentJobs.values.forEach { it.cancel() }
        intermittentJobs.clear()
        timerJob?.cancel()
        timerJob = null
        stopPlaylistScheduling()
        teardownMix()
        playerA.playWhenReady = false
        playerB.playWhenReady = false
        playerA.clearMediaItems()
        playerB.clearMediaItems()
        soundSettingsForPlayer.remove(playerA)
        soundSettingsForPlayer.remove(playerB)
        abandonAudioFocus()
        // Preserve lastSession so it resurfaces on next launch.
        _state.update {
            PlaybackState(
                appVolume = appVolume,
                duckOnNotifications = duckOnNotifications,
                lastSession = it.lastSession,
            )
        }
        demoteFromForeground()
        stopSelf()
    }

    /** Immediate pause — used for user button presses (and audio-focus loss).
     *  Sleep timer has its own gradual fade-out path instead. */
    fun pause() = pauseCurrentImmediate()

    private fun pauseCurrentImmediate() {
        val ms = _state.value.mixSession
        if (ms != null && mixPlayers.isNotEmpty()) {
            _state.update { it.copy(isPlaying = false) }
            refreshNotification()
            mixPlayers.forEach { p ->
                if (p.playWhenReady) {
                    fades[p]?.cancel()
                    p.playWhenReady = false
                }
            }
        } else {
            pauseActiveImmediate()
        }
    }

    private fun pauseActiveImmediate() {
        listOf(playerA, playerB).forEach { p ->
            if (p.playWhenReady) {
                fades[p]?.cancel()
                p.playWhenReady = false
            }
        }
    }

    private fun resumeCurrentWithFade() {
        // Guard against a spurious play-button tap while already playing (e.g. if the
        // lockscreen button briefly showed the wrong state). Otherwise resume resets
        // every layer's volume to 0 and fades up — an audible "restart".
        if (_state.value.isPlaying) return
        val ms = _state.value.mixSession
        if (ms != null && mixPlayers.isNotEmpty()) {
            mixPlayers.forEachIndexed { i, p ->
                val target = ms.currentVolumes.getOrElse(i) { 0f }
                setLogicalVolume(p, 0f)
                p.playWhenReady = true
                fade(p, 0f, target, fadeInDuration)
            }
            _state.update { it.copy(isPlaying = true) }
            refreshNotification()
            promoteToForeground()
        } else {
            resumeActiveWithFade()
        }
    }


    fun setSleepTimer(duration: Duration) {
        timerJob?.cancel()
        if (duration <= Duration.ZERO) {
            _state.update { it.copy(timerEndsAt = null, timerTotalMs = null) }
            return
        }
        val totalMs = duration.inWholeMilliseconds
        val endsAt = System.currentTimeMillis() + totalMs
        _state.update { it.copy(timerEndsAt = endsAt, timerTotalMs = totalMs) }
        timerJob = lifecycleScope.launch {
            while (true) {
                val remaining = endsAt - System.currentTimeMillis()
                if (remaining <= timerFadeOutDuration.inWholeMilliseconds) break
                delay(1_000)
            }
            // Fade out every currently-audible player (single/playlist/mix).
            val allPlayers = listOf(playerA, playerB) + mixPlayers
            val jobs = allPlayers.map { p ->
                launch {
                    fades[p]?.cancelAndJoin()
                    rampVolume(p, p.volume, 0f, timerFadeOutDuration)
                    p.playWhenReady = false
                }
            }
            jobs.forEach { it.join() }
            stopPlaylistScheduling()
            _state.update { it.copy(timerEndsAt = null, timerTotalMs = null) }
        }
    }

    fun setCrossfadeSeconds(seconds: Int) {
        crossfadeDuration = seconds.coerceIn(0, AppSettings.MAX_CROSSFADE_SEC).seconds
        AppSettings.setCrossfadeSeconds(this, seconds)
    }

    fun setFadeInSeconds(seconds: Int) {
        fadeInDuration = seconds.coerceIn(0, AppSettings.MAX_FADE_IN_SEC).seconds
        AppSettings.setFadeInSeconds(this, seconds)
    }

    fun setTimerFadeOutSeconds(seconds: Int) {
        timerFadeOutDuration = seconds.coerceIn(0, AppSettings.MAX_TIMER_FADE_OUT_SEC).seconds
        AppSettings.setTimerFadeOutSeconds(this, seconds)
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(timerEndsAt = null, timerTotalMs = null) }
    }

    // --- Playback internals ---

    private fun playOnActive(source: SoundSource, fadeIn: Boolean) {
        val p = activePlayer()
        val idle = idlePlayer()
        // Stop the idle player if it was still playing from a prior crossfade that got canceled.
        idle.playWhenReady = false
        idle.clearMediaItems()
        fades[idle]?.cancel()

        if (p.currentMediaItem?.mediaId != source.id) {
            p.setMediaItem(buildMediaItem(source))
            p.prepare()
        }
        applySoundSettings(p, source.id)
        setLogicalVolume(p, 0f)
        p.playWhenReady = true
        _state.update {
            it.copy(current = source)
        }
        if (fadeIn) {
            fade(p, 0f, 1f, fadeInDuration)
        } else {
            setLogicalVolume(p, 1f)
        }
    }

    private fun crossfadeTo(nextSource: SoundSource, nextIndex: Int) {
        val outgoing = activePlayer()
        val incoming = idlePlayer()

        incoming.setMediaItem(buildMediaItem(nextSource))
        incoming.prepare()
        applySoundSettings(incoming, nextSource.id)
        setLogicalVolume(incoming, 0f)
        incoming.playWhenReady = true

        // Swap which player is "active". Session follows the active player.
        activeIsA = !activeIsA
        mediaSession?.setPlayer(activeWrapped())

        _state.update {
            it.copy(
                current = nextSource,
                playlistSession = it.playlistSession?.copy(currentIndex = nextIndex),
            )
        }
        refreshNotification()

        fade(outgoing, outgoing.volume, 0f, crossfadeDuration)
        fade(incoming, 0f, 1f, crossfadeDuration)

        // After crossfade, stop the outgoing player cleanly.
        lifecycleScope.launch {
            fades[outgoing]?.join()
            outgoing.playWhenReady = false
            outgoing.clearMediaItems()
        }
    }

    private fun resumeActiveWithFade() {
        if (_state.value.isPlaying) return
        val p = activePlayer()
        if (p.currentMediaItem == null) return
        setLogicalVolume(p, 0f)
        p.playWhenReady = true
        fade(p, 0f, 1f, fadeInDuration)
        promoteToForeground()
    }


    private fun stopPlaylistScheduling() {
        playlistJob?.cancel()
        playlistJob = null
    }

    // --- Fade helpers ---

    private fun fade(p: ExoPlayer, from: Float, to: Float, duration: Duration): Job {
        fades[p]?.cancel()
        val j = lifecycleScope.launch {
            rampVolume(p, from, to, duration)
        }
        fades[p] = j
        return j
    }

    private suspend fun rampVolume(p: ExoPlayer, from: Float, to: Float, duration: Duration) {
        // Clamp to a minimum audible-safe ramp even when the setting is 0, to avoid
        // clicks/pops when a track starts mid-transient.
        val effective = if (duration < MIN_FADE) MIN_FADE else duration
        val steps = max(1, effective.inWholeMilliseconds / STEP_MS).toInt()
        val delta = (to - from) / steps
        repeat(steps) { i ->
            setLogicalVolume(p, from + delta * (i + 1))
            delay(STEP_MS)
        }
        setLogicalVolume(p, to)
    }

    // --- Foreground notification ---

    private fun promoteToForeground() {
        val notif = buildMediaNotification()
        if (!isForeground) {
            try {
                startForeground(
                    FreeDriftApp.NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
                isForeground = true
                Log.d(TAG, "promoted to foreground ok")
            } catch (e: Throwable) {
                Log.e(TAG, "startForeground FAILED", e)
            }
        } else {
            runCatching {
                NotificationManagerCompat.from(this).notify(FreeDriftApp.NOTIF_ID, notif)
            }
        }
    }

    private fun refreshNotification() {
        if (!isForeground) return
        runCatching {
            NotificationManagerCompat.from(this).notify(FreeDriftApp.NOTIF_ID, buildMediaNotification())
        }
    }

    private fun demoteFromForeground() {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
    }

    private fun buildMediaItem(source: SoundSource): MediaItem =
        MediaItem.Builder()
            .setUri(source.toUri())
            .setMediaId(source.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(source.displayName)
                    .setArtist(_state.value.playlistSession?.playlist?.name ?: getString(R.string.app_name))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun buildMediaItemForMix(source: SoundSource, mix: Mix, isPrimary: Boolean): MediaItem {
        // The PRIMARY layer is the one the MediaSession binds to, so its metadata is what
        // the lockscreen/notification shows. Give it the mix name. Others get layer names
        // (not shown, but useful for debugging / future layer switching UI).
        val metadata = MediaMetadata.Builder()
            .setTitle(if (isPrimary) mix.name else source.displayName)
            .setArtist(if (isPrimary) "${mix.layers.size} layers" else mix.name)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItem.Builder()
            .setUri(source.toUri())
            .setMediaId(source.id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildMediaNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val s = _state.value
        val title = when {
            s.mixSession != null -> s.mixSession.mix.name
            else -> s.current?.displayName ?: getString(R.string.app_name)
        }
        val subtitle = when {
            s.mixSession != null -> "${s.mixSession.mix.layers.size} layers"
            s.playlistSession != null -> "${s.playlistSession.playlist.name} · ${s.playlistSession.currentIndex + 1} of ${s.playlistSession.playlist.entries.size}"
            else -> getString(R.string.app_name)
        }
        val session = mediaSession
        val builder = NotificationCompat.Builder(this, FreeDriftApp.CHANNEL_PLAYBACK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (session != null) {
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session))
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "FreeDrift"
        private const val STEP_MS = 50L
        private val MIN_FADE: Duration = 50.milliseconds
    }
}

data class PlaybackState(
    val current: SoundSource? = null,
    val isPlaying: Boolean = false,
    val timerEndsAt: Long? = null,
    val timerTotalMs: Long? = null,
    val playlistSession: PlaylistSession? = null,
    val mixSession: MixSession? = null,
    val appVolume: Float = 1f,
    val duckOnNotifications: Boolean = false,
    val lastSession: LastSessionRef? = null,
)

data class PlaylistSession(
    val playlist: Playlist,
    val currentIndex: Int,
)

data class MixSession(
    val mix: Mix,
    /** Current volume per layer, parallel to mix.layers. Updated live as sliders move. */
    val currentVolumes: List<Float>,
)
