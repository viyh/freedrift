package io.github.viyh.freedrift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.viyh.freedrift.audio.Playlist
import io.github.viyh.freedrift.audio.Mix
import io.github.viyh.freedrift.audio.PlaylistEntry
import io.github.viyh.freedrift.audio.SoundSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditorScreen(
    initial: Playlist?,
    availableSounds: List<SoundSource>,
    availableMixes: List<Mix>,
    onSave: (Playlist) -> Unit,
    onCancel: () -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var entries by remember { mutableStateOf(initial?.entries ?: emptyList()) }
    var showAdd by remember { mutableStateOf(false) }

    BackHandler { onCancel() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "New playlist" else "Edit playlist") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onBackground)
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
                Text("Entries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAdd = true }) { Text("+ Add") }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (entries.isEmpty()) {
                    item {
                        Text(
                            "No entries yet. Tap + Add to pick a sound and set how long it plays before fading into the next one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                itemsIndexed(entries, key = { i, e -> "$i:${e.soundId}" }) { idx, entry ->
                    EntryRow(
                        entry = entry,
                        canUp = idx > 0,
                        canDown = idx < entries.size - 1,
                        onUp = {
                            entries = entries.toMutableList().also {
                                val tmp = it[idx - 1]; it[idx - 1] = it[idx]; it[idx] = tmp
                            }
                        },
                        onDown = {
                            entries = entries.toMutableList().also {
                                val tmp = it[idx + 1]; it[idx + 1] = it[idx]; it[idx] = tmp
                            }
                        },
                        onChangeDuration = { newDur ->
                            entries = entries.toMutableList().also {
                                it[idx] = entry.copy(durationMinutes = newDur)
                            }
                        },
                        onDelete = {
                            entries = entries.toMutableList().also { it.removeAt(idx) }
                        },
                    )
                }
            }

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
                        val p = (initial ?: Playlist(name = name.trim().ifBlank { "Untitled" }, entries = emptyList()))
                            .copy(name = name.trim().ifBlank { "Untitled" }, entries = entries)
                        onSave(p)
                    },
                    enabled = entries.isNotEmpty() && name.isNotBlank(),
                ) { Text("Save") }
            }
        }

        if (showAdd) {
            AddEntryDialog(
                availableSounds = availableSounds,
                availableMixes = availableMixes,
                onCancel = { showAdd = false },
                onAddSound = { source, minutes ->
                    entries = entries + PlaylistEntry(
                        soundId = source.id,
                        displayName = source.displayName,
                        durationMinutes = minutes,
                    )
                    showAdd = false
                },
                onAddMix = { mix, minutes ->
                    entries = entries + PlaylistEntry(
                        soundId = "mix:${mix.id}",
                        displayName = mix.name,
                        durationMinutes = minutes,
                    )
                    showAdd = false
                },
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: PlaylistEntry,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onChangeDuration: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var durationText by remember(entry.durationMinutes) { mutableStateOf(entry.durationMinutes.toString()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, color = MaterialTheme.colorScheme.onBackground)
            }
            OutlinedTextField(
                value = durationText,
                onValueChange = { v ->
                    durationText = v.filter { it.isDigit() }.take(4)
                    durationText.toIntOrNull()?.let(onChangeDuration)
                },
                label = { Text("min") },
                singleLine = true,
                modifier = Modifier.width(90.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
            )
            IconButton(onClick = onUp, enabled = canUp) {
                Icon(Icons.Default.ArrowUpward, "Move up", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDown, enabled = canDown) {
                Icon(Icons.Default.ArrowDownward, "Move down", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddEntryDialog(
    availableSounds: List<SoundSource>,
    availableMixes: List<Mix>,
    onCancel: () -> Unit,
    onAddSound: (SoundSource, Int) -> Unit,
    onAddMix: (Mix, Int) -> Unit,
) {
    var pickingMix by remember { mutableStateOf(false) }
    var selectedSound: SoundSource? by remember { mutableStateOf(null) }
    var selectedMix: Mix? by remember { mutableStateOf(null) }
    var durationText by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Segmented picker: Sound vs Mix
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindChip("Sound", selected = !pickingMix) {
                        pickingMix = false
                        selectedMix = null
                    }
                    KindChip("Mix", selected = pickingMix, enabled = availableMixes.isNotEmpty()) {
                        pickingMix = true
                        selectedSound = null
                    }
                }
                LazyColumn(
                    modifier = Modifier.height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (pickingMix) {
                        items(availableMixes) { m ->
                            PickRow(
                                label = "${m.name}  ·  ${m.layers.size} layers",
                                selected = selectedMix?.id == m.id,
                                onClick = { selectedMix = m },
                            )
                        }
                    } else {
                        items(availableSounds) { s ->
                            PickRow(
                                label = s.displayName,
                                selected = selectedSound?.id == s.id,
                                onClick = { selectedSound = s },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { v -> durationText = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Duration (minutes)") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val dur = durationText.toIntOrNull() ?: 0
            val canAdd = dur > 0 && ((pickingMix && selectedMix != null) || (!pickingMix && selectedSound != null))
            Button(
                onClick = {
                    if (pickingMix) selectedMix?.let { onAddMix(it, dur) }
                    else selectedSound?.let { onAddSound(it, dur) }
                },
                enabled = canAdd,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun KindChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = if (enabled) onClick else ({ }),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surface
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            label,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onBackground
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected)
            MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

