package io.github.viyh.freedrift.audio

import android.content.Context

/**
 * Ships a curated set of scenes. First launch seeds them; bumping STARTERS_VERSION
 * re-seeds (upsert by name) so shipped tweaks to the default layers land for
 * existing installs without disturbing user-created scenes.
 *
 * Sound lookups use SoundLibrary.compareKey (lowercased, alphanumerics only) with
 * a startsWith fallback. Missing layers are silently skipped; a spec whose layers
 * all go missing is skipped entirely.
 */
object StarterScenes {

    // Bump when the specs list changes. Launches with a stored version below
    // this one will try to add any new specs that aren't already in the user's
    // scene list (matched by scene name). User-renamed or user-deleted starter
    // scenes are left alone.
    private const val STARTERS_VERSION = 3

    private data class Spec(val name: String, val layers: List<LayerSpec>)
    private data class LayerSpec(val key: String, val volume: Float)

    private val specs = listOf(
        Spec("Japanese Garden", listOf(
            LayerSpec("shishiodoshi", 0.07f),
            LayerSpec("suikinkutsu", 0.40f),
            LayerSpec("stream", 0.45f),
            LayerSpec("windchimeslow", 0.20f),
            LayerSpec("birdshigh", 0.15f),
        )),
        Spec("Rainy Forest Night", listOf(
            LayerSpec("rainloudtrees", 0.65f),
            LayerSpec("thunder", 0.45f),
            LayerSpec("owl02", 0.20f),
            LayerSpec("owl03", 0.18f),
            LayerSpec("windstrongtrees", 0.25f),
            LayerSpec("noisegreen", 0.05f),
        )),
        Spec("Summer Forest Day", listOf(
            LayerSpec("birdshigh", 0.55f),
            LayerSpec("windgentleleaves", 0.35f),
            LayerSpec("stream", 0.30f),
        )),
        Spec("Country Summer Night", listOf(
            LayerSpec("crickets", 0.60f),
            LayerSpec("owl01", 0.18f),
            LayerSpec("owl04", 0.15f),
            LayerSpec("windgentleleaves", 0.25f),
        )),
        Spec("Cat by the Fireplace", listOf(
            LayerSpec("firecracklestrong", 0.55f),
            LayerSpec("catpurr", 0.35f),
            LayerSpec("windstronghowl", 0.20f),
        )),
        Spec("Stormy Night", listOf(
            LayerSpec("thunder", 0.55f),
            LayerSpec("raindownpourstreet", 0.65f),
            LayerSpec("windstronghowl", 0.35f),
            LayerSpec("noisebrown", 0.05f),
        )),
        Spec("Shore", listOf(
            LayerSpec("wavesbeachireland", 0.65f),
            LayerSpec("seagulls", 0.50f),
            LayerSpec("boatmooring1", 0.40f),
        )),
        Spec("Meditation", listOf(
            LayerSpec("singingbowlsingle", 0.50f),
            LayerSpec("suikinkutsu", 0.35f),
            LayerSpec("windchimeshigh", 0.50f),
        )),
        Spec("Cozy Morning", listOf(
            LayerSpec("raingarden", 0.55f),
            LayerSpec("birdsbinaural", 0.30f),
            LayerSpec("firecracklegentle", 0.35f),
        )),
        Spec("Spaceship", listOf(
            LayerSpec("noisebrown", 0.25f),
            LayerSpec("machinedrone01", 0.80f),
            LayerSpec("machinedrone02", 0.30f),
            LayerSpec("machinehigh", 0.40f),
            LayerSpec("electronicbeeps", 0.10f),
        )),
    )

    fun seedIfNeeded(context: Context) {
        val existingScenes = SceneRepository.load(context)
        val seededVersion = AppSettings.startersSeededVersion(context)
        val legacyFlag = AppSettings.startersSeeded(context)

        // Nothing to do if we've already seeded this version AND the scenes
        // store is non-empty (empty store catches migrations where the flag
        // survived but the scene prefs file didn't).
        if (seededVersion >= STARTERS_VERSION &&
            legacyFlag &&
            existingScenes.isNotEmpty()) {
            return
        }

        // Per-sound default settings come from assets/sound_defaults.json.
        SoundDefaults.apply(context)

        val library = SoundLibrary.all(context)
        val existingByName = existingScenes.associateBy { it.name }

        specs.forEach { spec ->
            val layers = spec.layers.mapNotNull { ls ->
                val match = findSound(library, ls.key) ?: return@mapNotNull null
                Layer(
                    soundId = match.id,
                    displayName = match.displayName,
                    defaultVolume = ls.volume,
                )
            }
            if (layers.isEmpty()) return@forEach
            // Preserve the original id for starter scenes that already exist so
            // any saved references (e.g. last-session pointer, playlist entries)
            // keep working after the refresh.
            val existingId = existingByName[spec.name]?.id
            val scene = if (existingId != null) {
                Scene(id = existingId, name = spec.name, layers = layers)
            } else {
                Scene(name = spec.name, layers = layers)
            }
            SceneRepository.upsert(context, scene)
        }

        AppSettings.setStartersSeeded(context, true)
        AppSettings.setStartersSeededVersion(context, STARTERS_VERSION)
    }

    private fun findSound(library: List<SoundSource>, desiredKey: String): SoundSource? {
        val target = SoundLibrary.compareKey(desiredKey)
        return library.firstOrNull { SoundLibrary.compareKey(it.displayName) == target }
            ?: library.firstOrNull { SoundLibrary.compareKey(it.displayName).startsWith(target) }
    }
}
