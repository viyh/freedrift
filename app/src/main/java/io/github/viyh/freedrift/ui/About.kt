package io.github.viyh.freedrift.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.viyh.freedrift.BuildConfig

private val APP_VERSION = BuildConfig.VERSION_NAME
private const val APP_AUTHOR = "Joe Richards"
private const val APP_GITHUB = "https://github.com/viyh/freedrift"
private const val APP_LICENSE = "GPL-3.0-or-later"

private data class SoundCredit(
    val title: String,
    val author: String,
    val url: String,
    val license: String,
)

private val SOUND_CREDITS = listOf(
    SoundCredit("Thunders HD", "Sempoo", "https://freesound.org/s/127242/", "CC0"),
    SoundCredit("Charming Birds in a Forest", "naturesoundspa", "https://freesound.org/s/163597/", "CC-BY 3.0"),
    SoundCredit("Calming Water Stream", "naturesoundspa", "https://freesound.org/s/163598/", "CC-BY 3.0"),
    SoundCredit("Owl Hoot (clean)", "musicmasta1", "https://freesound.org/s/251937/", "CC-BY-NC 3.0"),
    SoundCredit("Suikinkutsu at Eikan-do Zenrin-ji", "SDLx", "https://freesound.org/s/259993/", "CC-BY 3.0"),
    SoundCredit("Rain Without Thunder", "lebaston100", "https://freesound.org/s/346562/", "CC-BY 4.0"),
    SoundCredit("Ocean Waves, Seal Rock, Oregon", "Didgegirl6", "https://freesound.org/s/408686/", "CC-BY 3.0"),
    SoundCredit("Rain_03", "vincefred", "https://freesound.org/s/515839/", "CC0"),
    SoundCredit("Meadow Ambience", "eric5335", "https://freesound.org/s/53380/", "CC-BY-NC 3.0"),
    SoundCredit("Owl Hooting (×3 clips)", "Gerent", "https://freesound.org/s/558397/", "CC0"),
    SoundCredit("Deerchaser", "morgantj", "https://freesound.org/s/58569/", "CC-BY 4.0"),
    SoundCredit("Japanese Garden Stream", "morgantj", "https://freesound.org/s/58570/", "CC-BY 4.0"),
    SoundCredit("Rain Falling On The Greenhouse", "WhiteNoiseSleeper", "https://freesound.org/s/725603/", "CC0"),
    SoundCredit("Rain as heard from inside a greenhouse", "richwise", "https://freesound.org/s/724263/", "CC0"),
    SoundCredit("Cat Purr", "DudeAwesome", "https://freesound.org/s/790281/", "CC-BY 4.0"),
)

@Composable
fun AboutCard() {
    var creditsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "FreeDrift",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "v$APP_VERSION · by $APP_AUTHOR",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, APP_GITHUB.toUri()))
                    }
                }) { Text("GitHub") }
                TextButton(onClick = { creditsOpen = true }) { Text("Sound credits") }
            }
            Text(
                "Copyleft 2026 · $APP_LICENSE",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (creditsOpen) {
        SoundCreditsDialog(onDismiss = { creditsOpen = false })
    }
}

@Composable
private fun SoundCreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sound credits") },
        text = {
            LazyColumn(
                modifier = Modifier.height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(SOUND_CREDITS) { credit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, credit.url.toUri())
                                    )
                                }
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            credit.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${credit.author} · ${credit.license}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "All sounds from freesound.org. Tap a row to open its source.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
