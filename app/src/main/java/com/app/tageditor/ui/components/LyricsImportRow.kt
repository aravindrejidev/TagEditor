package com.app.tageditor.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.tageditor.data.LyricsFiles
import com.app.tageditor.ui.theme.NeuIcons
import com.app.tageditor.ui.theme.NeuTextSecondary

/**
 * Sits under the lyrics field: shows what kind of lyrics the field holds (plain, LRC, enhanced
 * LRC or TTML, and how many lines) and lets the user load a synced lyrics file into it.
 */
@Composable
fun LyricsImportRow(
    lyrics: String,
    onImport: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    // LRC / ELRC / TTML have no reliable MIME type, so every file is offered and the extension
    // is checked after picking.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    val summary = remember(lyrics) { LyricsFiles.analyse(lyrics) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (summary != null) {
            val pills = ArrayList<Pair<String, Boolean>>()
            pills.add(Pair(summary.format.label, true))
            if (summary.lines > 0) pills.add(Pair("${summary.lines} lines", false))
            PillRow(pills = pills, modifier = Modifier.padding(start = 6.dp))
        }
        NeuButton(
            text = "Import lyrics file",
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth(),
            icon = NeuIcons.Upload
        )
        Text(
            text = ".lrc, .elrc and .ttml files are stored in the tag exactly as they are, timestamps included.",
            style = MaterialTheme.typography.labelMedium,
            color = NeuTextSecondary,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
