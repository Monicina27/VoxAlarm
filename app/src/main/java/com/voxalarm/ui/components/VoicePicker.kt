package com.voxalarm.ui.components

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.voxalarm.tts.TtsState
import com.voxalarm.tts.VoiceInfo
import com.voxalarm.ui.theme.VoxBlack
import com.voxalarm.ui.theme.VoxCardHigh
import com.voxalarm.ui.theme.VoxOutline
import com.voxalarm.ui.theme.VoxTextDim
import java.util.Locale

/** Friendly but honest label for a TTS voice – built only from what the engine reports. */
fun VoiceInfo.friendlyName(): String {
    val code = Regex("-x-([a-z0-9]+)-(local|network)$").find(name)?.groupValues?.get(1)?.uppercase()
    val base = code ?: name
    return listOfNotNull(gender, base).joinToString(" · ")
}

fun VoiceInfo.details(): String = listOfNotNull(
    localeDisplay,
    if (requiresNetwork) "Needs internet" else "Offline",
    qualityLabel,
    if (notInstalled) "Not downloaded" else null,
).joinToString(" · ")

/**
 * Language + voice selection, driven entirely by the voices the installed
 * TTS engine reports. Blank values mean "use the device / engine default".
 */
@Composable
fun VoicePicker(
    tts: TtsState,
    languageTag: String,
    voiceName: String,
    onLanguageChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    color: Color,
) {
    val context = LocalContext.current
    when {
        tts.failed -> {
            Text(
                "No Text-to-Speech engine is available. Install or enable one (for example \"Speech Services by Google\") – alarms will play a tone until then.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.size(10.dp))
            VoiceEngineButtons(context, color)
            return
        }
        !tts.ready -> {
            Text("Loading installed voices…", style = MaterialTheme.typography.bodySmall, color = VoxTextDim)
            return
        }
    }

    val languages = remember(tts.voices) {
        tts.voices.map { it.languageTag to it.localeDisplay }.distinct()
            .sortedWith(compareBy({ !it.first.startsWith("en") }, { it.second }))
    }
    val effectiveLang = languageTag.ifBlank { Locale.getDefault().toLanguageTag() }
    val voicesForLang = remember(tts.voices, effectiveLang) {
        val exact = tts.voices.filter { it.languageTag.equals(effectiveLang, ignoreCase = true) }
        exact.ifEmpty {
            val lang = Locale.forLanguageTag(effectiveLang).language
            tts.voices.filter { Locale.forLanguageTag(it.languageTag).language == lang }
        }
    }

    val langLabel = if (languageTag.isBlank()) "Device default (${Locale.getDefault().displayName})"
    else languages.firstOrNull { it.first == languageTag }?.second ?: Locale.forLanguageTag(languageTag).displayName
    SelectorField(
        icon = Icons.Filled.Language,
        label = "Language & accent",
        value = langLabel,
        color = color,
        options = listOf(SelectorOption("", "Device default", Locale.getDefault().displayName)) +
            languages.map { SelectorOption(it.first, it.second, it.first) },
        onSelect = {
            onLanguageChange(it)
            onVoiceChange("") // voice belongs to a language
        },
    )
    Spacer(Modifier.size(10.dp))

    val selectedVoice = tts.voices.firstOrNull { it.name == voiceName }
    SelectorField(
        icon = Icons.Filled.RecordVoiceOver,
        label = "Voice",
        value = selectedVoice?.friendlyName() ?: "Engine default",
        supporting = selectedVoice?.details(),
        color = color,
        options = listOf(SelectorOption("", "Engine default", "Let the TTS engine choose")) +
            voicesForLang.map { SelectorOption(it.name, it.friendlyName(), it.details()) },
        onSelect = onVoiceChange,
    )
    if (voicesForLang.isEmpty()) {
        Text(
            "No voices reported for this language. You can download more in your TTS settings.",
            style = MaterialTheme.typography.bodySmall, color = VoxTextDim,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    Text(
        "Male/female labels appear only when your TTS engine reports them. Use Test Voice to hear each one.",
        style = MaterialTheme.typography.bodySmall, color = VoxTextDim,
        modifier = Modifier.padding(top = 8.dp),
    )
    Spacer(Modifier.size(10.dp))
    VoiceEngineButtons(context, color)
}

@Composable
private fun VoiceEngineButtons(context: Context, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeonOutlineButton(
            "Get voices", icon = Icons.Filled.CloudDownload, color = color, height = 40.dp,
            onClick = { context.launchSafely(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) },
        )
        NeonOutlineButton(
            "TTS settings", icon = Icons.Filled.Tune, color = color, height = 40.dp,
            onClick = { context.launchSafely(Intent("com.android.settings.TTS_SETTINGS")) },
        )
    }
}

fun Context.launchSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (t: Throwable) {
        Toast.makeText(this, "Not available on this device", Toast.LENGTH_SHORT).show()
    }
}

data class SelectorOption(val value: String, val title: String, val subtitle: String? = null)

@Composable
fun SelectorField(
    icon: ImageVector,
    label: String,
    value: String,
    options: List<SelectorOption>,
    onSelect: (String) -> Unit,
    color: Color,
    supporting: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(VoxBlack)
                .border(1.dp, VoxOutline, shape)
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = VoxTextDim)
                Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = VoxTextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Filled.ExpandMore, null, tint = VoxTextDim)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(VoxCardHigh).heightIn(max = 420.dp),
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(opt.title, style = MaterialTheme.typography.titleSmall)
                            if (opt.subtitle != null) {
                                Text(opt.subtitle, style = MaterialTheme.typography.bodySmall, color = VoxTextDim)
                            }
                        }
                    },
                    onClick = { open = false; onSelect(opt.value) },
                )
            }
        }
    }
}
