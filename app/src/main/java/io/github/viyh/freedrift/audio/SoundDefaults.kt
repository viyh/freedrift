package io.github.viyh.freedrift.audio

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Loads per-sound default playback settings from a bundled JSON config
 * (assets/sound_defaults.json) and applies them to any matching sound in the
 * library, unless the user has already customized that sound's settings.
 *
 * JSON shape:
 *   {
 *     "owl*":    { "mode": "intermittent", "minGapSec": 60 },
 *     "thunder1": { "mode": "intermittent", "minGapSec": 120 }
 *   }
 *
 * Keys are compared against each sound's normalized compareKey (lowercase,
 * alphanumeric only — same key we use elsewhere). A trailing `*` makes the
 * match a prefix (so "owl*" hits owl1, owl4, owlhoot, etc.). Keys starting
 * with `_` are treated as comments and ignored.
 */
object SoundDefaults {
    private const val ASSET = "sound_defaults.json"
    private const val TAG = "FreeDrift"

    fun apply(context: Context) {
        val json = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrElse {
            Log.w(TAG, "sound_defaults.json not found or unreadable; skipping")
            return
        }

        val obj = runCatching { JSONObject(json) }.getOrElse {
            Log.w(TAG, "sound_defaults.json malformed: ${it.message}")
            return
        }

        val library = SoundLibrary.all(context)
        val keys = obj.keys().asSequence().toList()
        for (pattern in keys) {
            if (pattern.startsWith("_")) continue // comment key
            val entry = obj.optJSONObject(pattern) ?: continue
            val settings = parseSettings(entry) ?: continue
            val matcher = buildMatcher(pattern)

            library.filter { matcher(SoundLibrary.compareKey(it.displayName)) }
                .forEach { source ->
                    val existing = SoundSettingsRepository.get(context, source.id)
                    // Don't stomp user customizations.
                    if (existing == SoundSettings()) {
                        SoundSettingsRepository.set(context, source.id, settings)
                    }
                }
        }
    }

    private fun buildMatcher(pattern: String): (String) -> Boolean {
        if (pattern.endsWith("*")) {
            val prefix = pattern.dropLast(1)
            return { key -> key.startsWith(prefix) }
        }
        return { key -> key == pattern }
    }

    private fun parseSettings(entry: JSONObject): SoundSettings? {
        val modeStr = entry.optString("mode", "continuous").lowercase()
        val mode = when (modeStr) {
            "continuous" -> SoundSettings.Mode.CONTINUOUS
            "intermittent" -> SoundSettings.Mode.INTERMITTENT
            else -> {
                Log.w(TAG, "unknown mode '$modeStr' in sound_defaults.json entry; skipping")
                return null
            }
        }
        val minGap = entry.optInt("minGapSec", 60)
            .coerceIn(SoundSettings.MIN_GAP_MIN_SEC, SoundSettings.MIN_GAP_MAX_SEC)
        return SoundSettings(mode = mode, minGapSec = minGap)
    }
}
