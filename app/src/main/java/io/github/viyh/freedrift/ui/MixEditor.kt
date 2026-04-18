package io.github.viyh.freedrift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.viyh.freedrift.audio.Layer
import io.github.viyh.freedrift.audio.Mix
import io.github.viyh.freedrift.audio.MixRepository
import io.github.viyh.freedrift.audio.SoundSource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixEditorScreen(
    initial: Mix?,
    availableSounds: List<SoundSource>,
    onSave: (Mix) -> Unit,
    onCancel: () -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var layers by remember { mutableStateOf(initial?.layers ?: emptyList()) }
    var showAdd by remember { mutableStateOf(false) }

    BackHandler { onCancel() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "New mix" else "Edit mix") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                )
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(pad)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Layers (${layers.size}/${MixRepository.MAX_LAYERS})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { showAdd = true },
                    enabled = layers.size < MixRepository.MAX_LAYERS,
                ) { Text("+ Add layer") }
            }

            if (layers.isEmpty()) {
                Text(
                    "No layers yet. Tap + Add layer to build your mix — rain, wind, thunder, birds, whatever you want combined.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LayerSliderBank(
                    layers = layers,
                    editable = true,
                    onVolumeChange = { i, v ->
                        layers = layers.toMutableList().also {
                            it[i] = it[i].copy(defaultVolume = v)
                        }
                    },
                    onRemove = { i ->
                        layers = layers.toMutableList().also { it.removeAt(i) }
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (initial != null && onDelete != null) {
                    OutlinedButton(onClick = { onDelete(initial.id) }) { Text("Delete") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(
                    onClick = {
                        val m = (initial ?: Mix(name = name.trim().ifBlank { "Untitled" }, layers = emptyList()))
                            .copy(name = name.trim().ifBlank { "Untitled" }, layers = layers)
                        onSave(m)
                    },
                    enabled = layers.isNotEmpty() && name.isNotBlank(),
                ) { Text("Save") }
            }
        }

        if (showAdd) {
            AddLayerDialog(
                availableSounds = availableSounds,
                onCancel = { showAdd = false },
                onAdd = { source ->
                    layers = layers + Layer(
                        soundId = source.id,
                        displayName = source.displayName,
                        defaultVolume = 0.5f,
                    )
                    showAdd = false
                },
            )
        }
    }
}

/**
 * Shared layer-bank UI: a horizontally-scrollable row of vertical sliders.
 * Used both in the editor (editable) and on the now-playing card (live).
 */
@Composable
fun LayerSliderBank(
    layers: List<Layer>,
    editable: Boolean,
    onVolumeChange: (Int, Float) -> Unit,
    onRemove: ((Int) -> Unit)? = null,
    currentVolumes: List<Float>? = null,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        layers.forEachIndexed { idx, layer ->
            val value = currentVolumes?.getOrNull(idx) ?: layer.defaultVolume
            LayerColumn(
                layer = layer,
                volume = value,
                onVolumeChange = { onVolumeChange(idx, it) },
                onRemove = if (editable && onRemove != null) {
                    { onRemove(idx) }
                } else null,
            )
        }
    }
}

@Composable
private fun LayerColumn(
    layer: Layer,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onRemove: (() -> Unit)?,
) {
    // Remember the last non-zero volume so unmuting restores to it rather than to
    // the layer's static default. Updated any time the user drags to a non-zero value.
    var preMuteVolume by remember(layer.soundId) {
        mutableFloatStateOf(
            if (volume > 0f) volume else layer.defaultVolume.coerceAtLeast(0.05f)
        )
    }
    val isMuted = volume <= 0f

    // Wrap the external onVolumeChange so dragging to non-zero always refreshes preMuteVolume.
    val onVolumeChangeTracking: (Float) -> Unit = { v ->
        if (v > 0f) preMuteVolume = v
        onVolumeChange(v)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width(72.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
        ) {
            IconButton(
                onClick = {
                    if (isMuted) onVolumeChange(preMuteVolume)
                    else onVolumeChange(0f)
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = if (isMuted)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                if (isMuted) "off" else "${(volume * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (isMuted)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            VerticalSlider(
                value = volume,
                onValueChange = onVolumeChangeTracking,
                modifier = Modifier.height(180.dp).width(48.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                layer.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(28.dp),
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Material3's Slider is horizontal. This rotates one by 270° using a custom layout
 * that swaps width/height constraints — the classic Compose vertical-slider pattern.
 */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier
                .rotate(270f)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(
                            x = -(placeable.width - placeable.height) / 2,
                            y = (placeable.width - placeable.height) / 2,
                        )
                    }
                },
        )
    }
}

@Composable
private fun AddLayerDialog(
    availableSounds: List<SoundSource>,
    onCancel: () -> Unit,
    onAdd: (SoundSource) -> Unit,
) {
    var selected: SoundSource? by remember { mutableStateOf(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add layer") },
        text = {
            LazyColumn(
                modifier = Modifier.height(280.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(availableSounds) { s ->
                    Surface(
                        onClick = { selected = s },
                        color = if (selected?.id == s.id)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            s.displayName,
                            color = if (selected?.id == s.id)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selected?.let(onAdd) },
                enabled = selected != null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}
