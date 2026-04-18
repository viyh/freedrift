package io.github.viyh.freedrift.audio

import android.content.Context

/**
 * Ships a small set of curated mixes. Seeded on first launch (or any launch where
 * the seeded-flag is still false). Matches desired sounds by normalized compare key,
 * falling back to startsWith so "oceanwaves" finds "Ocean Waves At Seal Rock OR".
 *
 * Any layer whose sound isn't present in the library is skipped; if all layers are
 * missing, the mix is skipped entirely. So partial asset sets produce partial starters.
 */
object StarterMixes {

    private data class Spec(val name: String, val layers: List<LayerSpec>)
    private data class LayerSpec(val key: String, val volume: Float)

    private val specs = listOf(
        Spec("Rainy Forest Night", listOf(
            LayerSpec("owl1", 0.25f),
            LayerSpec("thunder1", 0.50f),
            LayerSpec("rain6", 0.70f),
            LayerSpec("greennoise", 0.07f),
            LayerSpec("owl4", 0.15f),
        )),
        Spec("Ocean", listOf(
            LayerSpec("oceanwaves", 0.70f),
            LayerSpec("rain2", 0.40f),
        )),
        Spec("Garden", listOf(
            LayerSpec("stream1", 0.55f),
            LayerSpec("rain4", 0.40f),
            LayerSpec("shishiodoshi", 0.35f),
            LayerSpec("suikinkutsu", 0.35f),
        )),
    )

    fun seedIfNeeded(context: Context) {
        if (AppSettings.startersSeeded(context)) return

        // Per-sound default settings come from assets/sound_defaults.json.
        SoundDefaults.apply(context)

        val library = SoundLibrary.all(context)

        // Seed starter mixes.
        specs.forEach { spec ->
            val layers = spec.layers.mapNotNull { ls ->
                val match = findSound(library, ls.key) ?: return@mapNotNull null
                Layer(
                    soundId = match.id,
                    displayName = match.displayName,
                    defaultVolume = ls.volume,
                )
            }
            if (layers.isNotEmpty()) {
                MixRepository.upsert(context, Mix(name = spec.name, layers = layers))
            }
        }
        AppSettings.setStartersSeeded(context, true)
    }

    private fun findSound(library: List<SoundSource>, desiredKey: String): SoundSource? {
        val target = SoundLibrary.compareKey(desiredKey)
        return library.firstOrNull { SoundLibrary.compareKey(it.displayName) == target }
            ?: library.firstOrNull { SoundLibrary.compareKey(it.displayName).startsWith(target) }
    }
}
