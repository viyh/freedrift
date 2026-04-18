package io.github.viyh.freedrift.audio

import android.content.Context
import org.json.JSONObject

/**
 * Per-sound playback behavior. One mode, one config number.
 *
 * - [Mode.CONTINUOUS]: loops with a random start-offset on each iteration. Default.
 * - [Mode.INTERMITTENT]: plays start-to-end, then goes silent for random(min, 2*min)
 *   seconds, then plays again. [minGapSec] is only meaningful in this mode.
 */
data class SoundSettings(
    val mode: Mode = Mode.CONTINUOUS,
    val minGapSec: Int = 60,
) {
    enum class Mode { CONTINUOUS, INTERMITTENT }

    companion object {
        const val MIN_GAP_MIN_SEC = 5
        const val MIN_GAP_MAX_SEC = 600 // 10 min
    }
}

object SoundSettingsRepository {
    private const val PREFS = "sound_settings"
    private const val KEY = "data"

    fun get(context: Context, soundId: String): SoundSettings {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return SoundSettings()
        return runCatching {
            val obj = JSONObject(raw)
            if (!obj.has(soundId)) return@runCatching SoundSettings()
            val s = obj.getJSONObject(soundId)
            SoundSettings(
                mode = SoundSettings.Mode.valueOf(
                    s.optString("mode", SoundSettings.Mode.CONTINUOUS.name)
                ),
                minGapSec = s.optInt("minGapSec", 60)
                    .coerceIn(SoundSettings.MIN_GAP_MIN_SEC, SoundSettings.MIN_GAP_MAX_SEC),
            )
        }.getOrDefault(SoundSettings())
    }

    fun set(context: Context, soundId: String, settings: SoundSettings) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val obj = runCatching { JSONObject(prefs.getString(KEY, null) ?: "{}") }
            .getOrDefault(JSONObject())
        if (settings == SoundSettings()) {
            obj.remove(soundId)
        } else {
            obj.put(
                soundId,
                JSONObject()
                    .put("mode", settings.mode.name)
                    .put("minGapSec", settings.minGapSec)
            )
        }
        prefs.edit().putString(KEY, obj.toString()).apply()
    }

    fun all(context: Context): Map<String, SoundSettings> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { id ->
                    put(id, get(context, id))
                }
            }
        }.getOrDefault(emptyMap())
    }
}
