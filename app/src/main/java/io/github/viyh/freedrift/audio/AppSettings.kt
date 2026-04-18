package io.github.viyh.freedrift.audio

import android.content.Context

/**
 * User-tweakable settings, persisted in SharedPreferences.
 */
object AppSettings {
    private const val PREFS = "settings"

    private const val KEY_CROSSFADE_SEC = "crossfade_sec"
    private const val KEY_FADE_IN_SEC = "fade_in_sec"
    private const val KEY_TIMER_FADE_OUT_SEC = "timer_fade_out_sec"
    private const val KEY_APP_VOLUME = "app_volume"
    private const val KEY_DUCK_ON_NOTIFICATIONS = "duck_on_notifications"
    private const val KEY_LAST_SESSION = "last_session"
    private const val KEY_STARTERS_SEEDED = "starters_seeded"

    private const val DEFAULT_CROSSFADE_SEC = 8
    private const val DEFAULT_FADE_IN_SEC = 1
    private const val DEFAULT_TIMER_FADE_OUT_SEC = 30
    private const val DEFAULT_APP_VOLUME = 1f
    private const val DEFAULT_DUCK_ON_NOTIFICATIONS = false

    const val MAX_CROSSFADE_SEC = 30
    const val MAX_FADE_IN_SEC = 30
    const val MAX_TIMER_FADE_OUT_SEC = 120

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun crossfadeSeconds(c: Context): Int =
        prefs(c).getInt(KEY_CROSSFADE_SEC, DEFAULT_CROSSFADE_SEC).coerceIn(0, MAX_CROSSFADE_SEC)

    fun setCrossfadeSeconds(c: Context, v: Int) {
        prefs(c).edit().putInt(KEY_CROSSFADE_SEC, v.coerceIn(0, MAX_CROSSFADE_SEC)).apply()
    }

    fun fadeInSeconds(c: Context): Int =
        prefs(c).getInt(KEY_FADE_IN_SEC, DEFAULT_FADE_IN_SEC).coerceIn(0, MAX_FADE_IN_SEC)

    fun setFadeInSeconds(c: Context, v: Int) {
        prefs(c).edit().putInt(KEY_FADE_IN_SEC, v.coerceIn(0, MAX_FADE_IN_SEC)).apply()
    }

    fun timerFadeOutSeconds(c: Context): Int =
        prefs(c).getInt(KEY_TIMER_FADE_OUT_SEC, DEFAULT_TIMER_FADE_OUT_SEC)
            .coerceIn(0, MAX_TIMER_FADE_OUT_SEC)

    fun setTimerFadeOutSeconds(c: Context, v: Int) {
        prefs(c).edit().putInt(KEY_TIMER_FADE_OUT_SEC, v.coerceIn(0, MAX_TIMER_FADE_OUT_SEC)).apply()
    }

    fun appVolume(c: Context): Float =
        prefs(c).getFloat(KEY_APP_VOLUME, DEFAULT_APP_VOLUME).coerceIn(0f, 1f)

    fun setAppVolume(c: Context, v: Float) {
        prefs(c).edit().putFloat(KEY_APP_VOLUME, v.coerceIn(0f, 1f)).apply()
    }

    fun duckOnNotifications(c: Context): Boolean =
        prefs(c).getBoolean(KEY_DUCK_ON_NOTIFICATIONS, DEFAULT_DUCK_ON_NOTIFICATIONS)

    fun setDuckOnNotifications(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean(KEY_DUCK_ON_NOTIFICATIONS, v).apply()
    }

    /** Serialized "kind:targetId" e.g. "sound:asset:rain.ogg", "playlist:<uuid>", "scene:<uuid>". */
    fun lastSession(c: Context): LastSessionRef? {
        val raw = prefs(c).getString(KEY_LAST_SESSION, null) ?: return null
        val parts = raw.split(":", limit = 2)
        if (parts.size != 2) return null
        val kind = when (parts[0]) {
            "sound" -> LastSessionRef.Kind.SOUND
            "playlist" -> LastSessionRef.Kind.PLAYLIST
            "scene" -> LastSessionRef.Kind.SCENE
            else -> return null
        }
        return LastSessionRef(kind, parts[1])
    }

    fun setLastSession(c: Context, ref: LastSessionRef) {
        val prefix = when (ref.kind) {
            LastSessionRef.Kind.SOUND -> "sound"
            LastSessionRef.Kind.PLAYLIST -> "playlist"
            LastSessionRef.Kind.SCENE -> "scene"
        }
        prefs(c).edit().putString(KEY_LAST_SESSION, "$prefix:${ref.targetId}").apply()
    }

    fun clearLastSession(c: Context) {
        prefs(c).edit().remove(KEY_LAST_SESSION).apply()
    }

    fun startersSeeded(c: Context): Boolean =
        prefs(c).getBoolean(KEY_STARTERS_SEEDED, false)

    fun setStartersSeeded(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean(KEY_STARTERS_SEEDED, v).apply()
    }
}

data class LastSessionRef(val kind: Kind, val targetId: String) {
    enum class Kind { SOUND, PLAYLIST, SCENE }
}
