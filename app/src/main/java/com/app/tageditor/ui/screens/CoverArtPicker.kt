package com.app.tageditor.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.tageditor.data.CoverArt
import com.app.tageditor.data.ImageTools
import com.app.tageditor.ui.components.CreateFile
import com.app.tageditor.ui.components.NeuButton
import com.app.tageditor.ui.components.NeuCard
import com.app.tageditor.ui.components.NeuChip
import com.app.tageditor.ui.components.NeuSlider
import com.app.tageditor.ui.components.NeuSwitchRow
import com.app.tageditor.ui.components.PillRow
import com.app.tageditor.ui.components.rememberCoverBitmap
import com.app.tageditor.ui.formatSize
import com.app.tageditor.ui.theme.NeuIcons
import com.app.tageditor.ui.theme.NeuTextHint
import com.app.tageditor.ui.theme.NeuTextPrimary
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.ui.theme.neuInset
import kotlin.math.roundToInt

/**
 * Cover art manager: shows the embedded picture, lets the user replace it with an image from the
 * gallery, save it to a file, or remove it. [cover] is what will be embedded after saving.
 */
@Composable
fun CoverArtPicker(
    cover: CoverArt?,
    changed: Boolean,
    maxPx: Int,
    keepOriginal: Boolean,
    onImagePicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    onUndo: () -> Unit,
    onMaxPxChange: (Int) -> Unit,
    onKeepOriginalChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onExport: ((Uri) -> Unit)? = null,
    title: String = "Album art",
    emptyLabel: String = "No cover art",
    removeLabel: String = "Remove"
) {
    val bitmap by rememberCoverBitmap(cover)
    val bounds = remember(cover) { if (cover != null) ImageTools.bounds(cover.bytes) else null }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImagePicked(uri)
    }
    val createFile = rememberLauncherForActivityResult(remember { CreateFile() }) { uri ->
        if (uri != null) onExport?.invoke(uri)
    }

    NeuCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NeuTextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (changed) {
                NeuChip(text = "Undo", onClick = onUndo)
            }
        }
        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .neuInset(corner = 22.dp, depth = 5.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = "Cover art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = NeuIcons.MusicNote,
                        contentDescription = null,
                        tint = NeuTextHint,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = emptyLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = NeuTextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }

        if (cover != null) {
            Spacer(Modifier.height(14.dp))
            val facts = ArrayList<Pair<String, Boolean>>()
            facts.add(Pair(if (cover.mimeType.contains("png")) "PNG" else "JPEG", true))
            if (bounds != null) facts.add(Pair("${bounds.first} × ${bounds.second} px", false))
            facts.add(Pair(formatSize(cover.bytes.size.toLong()), false))
            PillRow(pills = facts, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        Spacer(Modifier.height(18.dp))
        NeuButton(
            text = if (cover == null) "Add image from gallery" else "Replace from gallery",
            onClick = {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth(),
            icon = NeuIcons.Image
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onExport != null) {
                NeuButton(
                    text = "Save image",
                    onClick = {
                        if (cover != null) createFile.launch(Pair("cover." + cover.extension, cover.mimeType))
                    },
                    modifier = Modifier.weight(1f),
                    icon = NeuIcons.Download,
                    enabled = cover != null
                )
            }
            NeuButton(
                text = removeLabel,
                onClick = onRemove,
                modifier = Modifier.weight(1f),
                icon = NeuIcons.Delete,
                danger = true,
                enabled = cover != null || onExport == null
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Largest side of new covers",
                style = MaterialTheme.typography.labelMedium,
                color = NeuTextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (keepOriginal) "original" else "$maxPx px",
                style = MaterialTheme.typography.labelLarge,
                color = NeuTextPrimary
            )
        }
        Spacer(Modifier.height(8.dp))
        NeuSlider(
            value = maxPx.toFloat(),
            onValueChange = { onMaxPxChange(it.roundToInt()) },
            valueRange = 300f..2000f,
            steps = 16,
            enabled = !keepOriginal
        )
        Spacer(Modifier.height(14.dp))
        NeuSwitchRow(
            title = "Keep original image",
            subtitle = "Embed the picked JPEG/PNG without resizing",
            checked = keepOriginal,
            onCheckedChange = onKeepOriginalChange
        )
    }
}
