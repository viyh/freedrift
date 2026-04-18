package io.github.viyh.freedrift.audio

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri

sealed interface SoundSource {
    val id: String
    val displayName: String
    fun toUri(): Uri

    data class Asset(val fileName: String, override val displayName: String) : SoundSource {
        override val id: String get() = "asset:$fileName"
        override fun toUri(): Uri = "asset:///sounds/$fileName".toUri()
    }

    data class UserFile(val uri: Uri, override val displayName: String) : SoundSource {
        override val id: String get() = "user:$uri"
        override fun toUri(): Uri = uri
    }
}

object SoundLibrary {
    private const val PREFS = "user_sounds"
    private const val KEY = "entries"

    private val AUDIO_EXTS = setOf("ogg", "opus", "flac", "mp3", "wav")

    /**
     * The only entry point UI code should use. Returns the merged, prettified, and
     * collision-disambiguated list of sounds (bundled + user-added), sorted by display name.
     */
    fun all(context: Context): List<SoundSource> {
        data class Raw(
            val sortKey: String,
            val rawStem: String,
            val build: (String) -> SoundSource,
        )

        val raws = mutableListOf<Raw>()

        // Bundled assets
        runCatching {
            context.assets.list("sounds")
                ?.filter { it.substringAfterLast('.', "").lowercase() in AUDIO_EXTS }
                ?.forEach { fileName ->
                    val stem = fileName.substringBeforeLast('.')
                    raws += Raw(
                        sortKey = fileName,
                        rawStem = stem,
                        build = { display -> SoundSource.Asset(fileName, display) },
                    )
                }
        }

        // User-added sounds (stored as "uri|displayName" pairs in prefs).
        rawUserEntries(context).forEach { (uri, rawName) ->
            raws += Raw(
                sortKey = rawName,
                rawStem = rawName,
                build = { display -> SoundSource.UserFile(uri, display) },
            )
        }

        // Group by compareKey and disambiguate collisions.
        val grouped = raws.groupBy { compareKey(it.rawStem) }
        val out = mutableListOf<SoundSource>()
        for ((_, group) in grouped) {
            val sorted = group.sortedBy { it.sortKey.lowercase() }
            if (sorted.size == 1) {
                val r = sorted[0]
                out += r.build(prettify(r.rawStem))
            } else {
                // Pick the most-whitespace-containing pretty name as the shared base.
                val prettyCandidates = sorted.map { prettify(it.rawStem) }
                val base = prettyCandidates.maxBy { it.count(Char::isWhitespace) }
                sorted.forEachIndexed { i, r ->
                    out += r.build("$base.$i")
                }
            }
        }
        return out.sortedBy { it.displayName.lowercase() }
    }

    fun addUserSound(context: Context, uri: Uri, rawDisplayName: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        // Dedupe by URI — if the same URI is re-added, replace its display name.
        existing.removeAll { it.startsWith("$uri|") }
        existing.add("$uri|$rawDisplayName")
        prefs.edit().putStringSet(KEY, existing).apply()
    }

    fun removeUserSound(context: Context, source: SoundSource.UserFile) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY, emptySet())?.toMutableSet() ?: return
        existing.removeAll { it.startsWith("${source.uri}|") }
        prefs.edit().putStringSet(KEY, existing).apply()
    }

    private fun rawUserEntries(context: Context): List<Pair<Uri, String>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        return raw.mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size == 2) parts[0].toUri() to parts[1] else null
        }
    }

    // ----- Pretty-name helpers -----

    private val LETTER_DIGIT = Regex("([A-Za-z])([0-9])")
    private val DIGIT_LETTER = Regex("([0-9])([A-Za-z])")
    private val LOWER_UPPER = Regex("([a-z])([A-Z])")   // camelCase split
    private val WHITESPACE = Regex("\\s+")

    fun prettify(rawStem: String): String {
        // Drop a trailing extension if one snuck in (for user-entered display names with extensions).
        val stem = if (rawStem.substringAfterLast('.', "").lowercase() in AUDIO_EXTS)
            rawStem.substringBeforeLast('.') else rawStem
        var s = stem.replace('_', ' ').replace('-', ' ')
        s = LOWER_UPPER.replace(s, "$1 $2")
        s = LETTER_DIGIT.replace(s, "$1 $2")
        s = DIGIT_LETTER.replace(s, "$1 $2")
        s = WHITESPACE.replace(s, " ").trim()
        return s.split(' ').joinToString(" ") { w ->
            if (w.isEmpty()) "" else w[0].uppercase() + w.substring(1).lowercase()
        }
    }

    fun compareKey(rawStem: String): String {
        val stem = if (rawStem.substringAfterLast('.', "").lowercase() in AUDIO_EXTS)
            rawStem.substringBeforeLast('.') else rawStem
        return stem.lowercase().filter { it.isLetterOrDigit() }
    }
}
