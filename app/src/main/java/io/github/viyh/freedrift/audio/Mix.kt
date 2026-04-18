package io.github.viyh.freedrift.audio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Mix(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val layers: List<Layer>,
)

data class Layer(
    val soundId: String,
    val displayName: String,
    val defaultVolume: Float,
)

object MixRepository {
    private const val PREFS = "mixes"
    private const val KEY = "data"
    const val MAX_LAYERS = 8

    fun load(context: Context): List<Mix> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val layersArr = o.getJSONArray("layers")
                Mix(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    layers = List(layersArr.length()) { j ->
                        val l = layersArr.getJSONObject(j)
                        Layer(
                            soundId = l.getString("soundId"),
                            displayName = l.getString("displayName"),
                            defaultVolume = l.getDouble("defaultVolume").toFloat().coerceIn(0f, 1f),
                        )
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, mixes: List<Mix>) {
        val arr = JSONArray()
        mixes.forEach { m ->
            val o = JSONObject()
                .put("id", m.id)
                .put("name", m.name)
            val layersArr = JSONArray()
            m.layers.forEach { l ->
                layersArr.put(
                    JSONObject()
                        .put("soundId", l.soundId)
                        .put("displayName", l.displayName)
                        .put("defaultVolume", l.defaultVolume.toDouble())
                )
            }
            o.put("layers", layersArr)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    fun upsert(context: Context, mix: Mix) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == mix.id }
        if (idx >= 0) list[idx] = mix else list.add(mix)
        save(context, list)
    }

    fun delete(context: Context, id: String) {
        save(context, load(context).filter { it.id != id })
    }
}
