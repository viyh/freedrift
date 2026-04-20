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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.viyh.freedrift.BuildConfig

private val APP_VERSION = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})"
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
    SoundCredit("Japanese Garden Stream", "morgantj", "https://freesound.org/s/58570/", "CC-BY 4.0"),
    SoundCredit("Rain Falling On The Greenhouse", "WhiteNoiseSleeper", "https://freesound.org/s/725603/", "CC0"),
    SoundCredit("Rain as heard from inside a greenhouse", "richwise", "https://freesound.org/s/724263/", "CC0"),
    SoundCredit("Cat Purr", "DudeAwesome", "https://freesound.org/s/790281/", "CC-BY 4.0"),
    SoundCredit("Ambiance Waterfall Small Close Loop Stereo", "Nox_Sound", "https://freesound.org/s/698306/", "CC0"),
    SoundCredit("Running Stream in a Wood — Youghal, Co. Cork, Ireland", "midaza.com", "https://freesound.org/s/387412/", "CC-BY 4.0"),
    SoundCredit("Bubbling Brook (loop)", "NickTayloe", "https://freesound.org/s/847851/", "CC-BY 4.0"),
    SoundCredit("Closer Ocean Waves on Pebbly Beach — Iceland (loop)", "NickTayloe", "https://freesound.org/s/830254/", "CC-BY 4.0"),
    SoundCredit("seagulls.WAV", "inchadney", "https://freesound.org/s/78389/", "CC-BY 4.0"),
    SoundCredit("Binaural Birds LOOP 1", "maarten91", "https://freesound.org/s/468449/", "CC0"),
    SoundCredit("UnderTreeInRain", "acclivity", "https://freesound.org/s/28283/", "CC-BY-NC 4.0"),
    SoundCredit("Ambiance Wind Trees Leaves Moderate Loop Stereo", "Nox_Sound", "https://freesound.org/s/546759/", "CC0"),
    SoundCredit("Wind", "acclivity", "https://freesound.org/s/22818/", "CC-BY-NC 4.0"),
    SoundCredit("wind_forest_08_strong_l_02", "teadrinker", "https://freesound.org/s/403050/", "CC0"),
    SoundCredit("wind draft loop 3", "nsstudios", "https://freesound.org/s/651545/", "CC-BY 4.0"),
    SoundCredit("Singingbowl 108062", "smilecat77", "https://freesound.org/s/475307/", "CC0"),
    SoundCredit("Sound-Bowl-knee-right F#2", "Cpt_Asteroid", "https://freesound.org/s/138677/", "CC-BY-NC 4.0"),
    SoundCredit("Best Hypnotic Wind Chimes — Long Nice Loop", "DudeAwesome", "https://freesound.org/s/386470/", "CC-BY 4.0"),
    SoundCredit("Chimes", "juskiddink", "https://freesound.org/s/131979/", "CC-BY 4.0"),
    SoundCredit("Campfire 02", "HECKFRICKER", "https://freesound.org/s/729396/", "CC0"),
    SoundCredit("Loopedfire1", "stratcat322", "https://freesound.org/s/233189/", "CC-BY 3.0"),
    SoundCredit("coolingvessel_loop", "eardeer", "https://freesound.org/s/401680/", "CC-BY 4.0"),
    SoundCredit("Bubbles3_Zoom", "Greener_Sounds", "https://freesound.org/s/672393/", "CC0"),
    SoundCredit("bubbles.deep.loop", "xUMR", "https://freesound.org/s/478653/", "CC0"),
    SoundCredit("Industrial Noises Ambient Sound 1", "SolarPhasing", "https://freesound.org/s/427504/", "CC-BY 4.0"),
    SoundCredit("YP Plague Noise Loop 08", "nlux", "https://freesound.org/s/623074/", "CC-BY 4.0"),
    SoundCredit("Sci Fi Drone Engine Loop", "steaq", "https://freesound.org/s/509249/", "CC0"),
    SoundCredit("SciFi Computer-1", "Andrewthomson", "https://freesound.org/s/100674/", "Sampling+"),
    SoundCredit("Moored sailboat interior in strong breeze", "AugustSandberg", "https://freesound.org/s/265553/", "CC0"),
    SoundCredit("wind-in-the-bamboos", "ebanse", "https://freesound.org/s/345681/", "CC-BY-NC 4.0"),
    SoundCredit("bamboo_wind_chimes_1", "jppi_Stu", "https://freesound.org/s/17090/", "CC-BY 4.0"),
)

@Composable
fun AboutCard() {
    var creditsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "About",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "FreeDrift",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "A minimal, free sleep-sounds app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PrivacyLine("No ads, ever.")
                PrivacyLine("No tracking, analytics, or telemetry.")
                PrivacyLine("No data collected. Nothing leaves your phone.")
            }
            Spacer(Modifier.height(16.dp))
            AboutMetaRow("Version", APP_VERSION)
            AboutMetaRow("Author", APP_AUTHOR)
            AboutMetaRow("License", APP_LICENSE)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, APP_GITHUB.toUri()))
                    }
                }) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub")
                }
                TextButton(onClick = { creditsOpen = true }) {
                    Icon(
                        Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sound credits")
                }
            }
        }
    }

    if (creditsOpen) {
        SoundCreditsDialog(onDismiss = { creditsOpen = false })
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun AboutMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
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
