package io.github.viyh.freedrift.audio

import android.content.Context
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val entries: List<PlaylistEntry>,
) {
    val totalMinutes: Int get() = entries.sumOf { it.durationMinutes }
}

data class PlaylistEntry(
    /** "asset:...", "user:<uri>", or "scene:<sceneId>". Generic target id. */
    val soundId: String,
    /** Cached so the entry still shows something if the source vanishes. */
    val displayName: String,
    val durationMinutes: Int,
    /**
     * Stable identity for in-memory list operations (drag-reorder, LazyColumn
     * keys). Not serialized — regenerated on load. A playlist can legitimately
     * contain the same soundId twice, so we can't key by that alone.
     */
    val localKey: String = UUID.randomUUID().toString(),
) {
    val isScene: Boolean get() = soundId.startsWith("scene:")
    val sceneId: String? get() = if (isScene) soundId.removePrefix("scene:") else null
}

fun resolveSoundSource(context: Context, soundId: String): SoundSource? {
    // Exact match on the disambiguated library.
    SoundLibrary.all(context).firstOrNull { it.id == soundId }?.let { return it }
    // Fall back to a minimal placeholder if the source has been removed — keeps playlist
    // entries and saved scenes functional but with a sentinel display name.
    return when {
        soundId.startsWith("asset:") ->
            SoundSource.Asset(soundId.removePrefix("asset:"), "(missing sound)")
        soundId.startsWith("user:") ->
            SoundSource.UserFile(soundId.removePrefix("user:").toUri(), "(missing sound)")
        else -> null
    }
}

object PlaylistRepository {
    private const val PREFS = "playlists"
    private const val KEY = "data"

    fun load(context: Context): List<Playlist> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val entriesArr = o.getJSONArray("entries")
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    entries = List(entriesArr.length()) { j ->
                        val e = entriesArr.getJSONObject(j)
                        PlaylistEntry(
                            soundId = e.getString("soundId"),
                            displayName = e.getString("displayName"),
                            durationMinutes = e.getInt("durationMinutes"),
                        )
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, playlists: List<Playlist>) {
        val arr = JSONArray()
        playlists.forEach { p ->
            val o = JSONObject()
                .put("id", p.id)
                .put("name", p.name)
            val entriesArr = JSONArray()
            p.entries.forEach { e ->
                entriesArr.put(
                    JSONObject()
                        .put("soundId", e.soundId)
                        .put("displayName", e.displayName)
                        .put("durationMinutes", e.durationMinutes)
                )
            }
            o.put("entries", entriesArr)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    fun upsert(context: Context, playlist: Playlist) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == playlist.id }
        if (idx >= 0) list[idx] = playlist else list.add(playlist)
        save(context, list)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filter { it.id != id })
    }
}
