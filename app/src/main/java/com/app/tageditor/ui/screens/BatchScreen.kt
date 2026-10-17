package com.app.tageditor.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.tageditor.data.TagKey
import com.app.tageditor.ui.components.NeuButton
import com.app.tageditor.ui.components.NeuCard
import com.app.tageditor.ui.components.NeuCheckbox
import com.app.tageditor.ui.components.NeuSwitchRow
import com.app.tageditor.ui.components.NeuTextField
import com.app.tageditor.ui.components.NeuTopBar
import com.app.tageditor.ui.components.SectionLabel
import com.app.tageditor.ui.theme.NeuIcons
import com.app.tageditor.ui.theme.NeuTextPrimary
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.viewmodel.BatchDraft
import com.app.tageditor.viewmodel.CoverMode
import com.app.tageditor.viewmodel.FieldDraft

private class BatchField(
    val key: TagKey,
    val label: String,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val capitalization: KeyboardCapitalization = KeyboardCapitalization.Words
)

private const val OVERVIEW_LIMIT = 6

private val batchFields = listOf(
    BatchField(TagKey.ALBUM, "Album"),
    BatchField(TagKey.ALBUM_ARTIST, "Album artist"),
    BatchField(TagKey.ARTIST, "Artist"),
    BatchField(TagKey.YEAR, "Year", KeyboardType.Number, KeyboardCapitalization.None),
    BatchField(TagKey.GENRE, "Genre"),
    BatchField(TagKey.DISC, "Disc number", KeyboardType.Text, KeyboardCapitalization.None),
    BatchField(TagKey.COMPOSER, "Composer"),
    BatchField(TagKey.COMMENT, "Comment", KeyboardType.Text, KeyboardCapitalization.Sentences)
)

/**
 * Update common tags of many files at once. A field is only written when its box is ticked
 * (typing ticks it automatically); a ticked empty field clears that tag on every file.
 */
@Composable
fun BatchScreen(
    fileCount: Int,
    draft: BatchDraft,
    coverMaxPx: Int,
    keepOriginalCover: Boolean,
    onBack: () -> Unit,
    onFieldChange: (TagKey, FieldDraft) -> Unit,
    onNumberTracks: (Boolean) -> Unit,
    onPickCover: (Uri) -> Unit,
    onCoverMode: (CoverMode) -> Unit,
    onMaxPxChange: (Int) -> Unit,
    onKeepOriginalChange: (Boolean) -> Unit,
    onApply: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NeuTopBar(
            title = "Batch edit",
            subtitle = "$fileCount files selected",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (draft.loading) {
                    "Reading the current tags ..."
                } else {
                    "Values are shown when every selected file agrees, otherwise the field lists what differs. Edit a field or tick its box to write it to all files."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = NeuTextSecondary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            if (draft.overview.isNotEmpty()) {
                NeuCard(modifier = Modifier.fillMaxWidth()) {
                    SectionLabel("Current data")
                    Spacer(Modifier.height(8.dp))
                    for ((item, tags) in draft.overview.take(OVERVIEW_LIMIT)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = tags.title.ifBlank { item.baseName },
                                style = MaterialTheme.typography.titleMedium,
                                color = NeuTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val details = listOf(tags.artist, tags.album, tags.year)
                                .filter { it.isNotBlank() }
                                .joinToString(" - ")
                            if (details.isNotEmpty()) {
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeuTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (draft.overview.size > OVERVIEW_LIMIT) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "and ${draft.overview.size - OVERVIEW_LIMIT} more",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeuTextSecondary
                        )
                    }
                }
            }

            for (field in batchFields) {
                val current = draft.field(field.key)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    NeuCheckbox(
                        checked = current.enabled,
                        onCheckedChange = { onFieldChange(field.key, current.copy(enabled = it)) }
                    )
                    NeuTextField(
                        value = current.value,
                        onValueChange = { onFieldChange(field.key, FieldDraft(enabled = true, value = it)) },
                        label = field.label,
                        modifier = Modifier.weight(1f),
                        hint = when {
                            current.enabled && current.value.isBlank() -> "Will be cleared on every file"
                            !current.enabled && current.value.isBlank() -> {
                                val different = draft.variants[field.key]
                                if (different != null) "Different: " + different.joinToString(" / ") else "Unchanged"
                            }
                            else -> null
                        },
                        keyboardType = field.keyboardType,
                        capitalization = field.capitalization
                    )
                }
            }

            NeuCard(modifier = Modifier.fillMaxWidth()) {
                SectionLabel("Track numbers")
                Spacer(Modifier.height(10.dp))
                NeuSwitchRow(
                    title = "Number tracks automatically",
                    subtitle = "Writes 1/$fileCount, 2/$fileCount ... in the order of your file list",
                    checked = draft.numberTracks,
                    onCheckedChange = onNumberTracks
                )
            }

            CoverArtPicker(
                cover = if (draft.coverMode == CoverMode.REPLACE) draft.newCover else null,
                changed = draft.coverMode != CoverMode.KEEP,
                maxPx = coverMaxPx,
                keepOriginal = keepOriginalCover,
                onImagePicked = onPickCover,
                onRemove = { onCoverMode(CoverMode.REMOVE) },
                onUndo = { onCoverMode(CoverMode.KEEP) },
                onMaxPxChange = onMaxPxChange,
                onKeepOriginalChange = onKeepOriginalChange,
                modifier = Modifier.fillMaxWidth(),
                title = "Cover art for all files",
                emptyLabel = if (draft.coverMode == CoverMode.REMOVE) {
                    "Cover will be removed from every file"
                } else {
                    "Covers stay unchanged"
                },
                removeLabel = "Remove cover from all"
            )
            Spacer(Modifier.height(8.dp))
        }

        NeuButton(
            text = "Apply to $fileCount files",
            onClick = onApply,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            icon = NeuIcons.Check,
            primary = true,
            enabled = draft.hasChanges
        )
    }
}
