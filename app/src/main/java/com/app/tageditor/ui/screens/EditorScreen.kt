package com.app.tageditor.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.app.tageditor.data.TagKey
import com.app.tageditor.ui.components.NeuButton
import com.app.tageditor.ui.components.NeuCard
import com.app.tageditor.ui.components.NeuProgressBar
import com.app.tageditor.ui.components.NeuTextField
import com.app.tageditor.ui.components.NeuTopBar
import com.app.tageditor.ui.components.NeuWell
import com.app.tageditor.ui.components.PillRow
import com.app.tageditor.ui.components.SectionLabel
import com.app.tageditor.ui.infoPills
import com.app.tageditor.ui.theme.NeuError
import com.app.tageditor.ui.theme.NeuIcons
import com.app.tageditor.ui.theme.NeuTextPrimary
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.viewmodel.EditorState

/** Edit every tag of a single file, including its embedded cover art. */
@Composable
fun EditorScreen(
    editor: EditorState,
    coverMaxPx: Int,
    keepOriginalCover: Boolean,
    onBack: () -> Unit,
    onEdit: (TagKey, String) -> Unit,
    onSave: () -> Unit,
    onRevert: () -> Unit,
    onRenameFromTags: () -> Unit,
    onPickCover: (Uri) -> Unit,
    onRemoveCover: () -> Unit,
    onUndoCover: () -> Unit,
    onExportCover: (Uri) -> Unit,
    onMaxPxChange: (Int) -> Unit,
    onKeepOriginalChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        NeuTopBar(
            title = editor.item.name,
            subtitle = if (editor.dirty) "Unsaved changes" else editor.item.folder.ifEmpty { "Tag editor" },
            onBack = onBack
        )

        when {
            editor.loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    NeuCard(modifier = Modifier.padding(32.dp)) {
                        Text(
                            text = "Reading tags",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeuTextPrimary
                        )
                        Spacer(Modifier.height(14.dp))
                        NeuProgressBar(progress = null, modifier = Modifier.padding(horizontal = 24.dp))
                    }
                }
            }

            editor.loadError != null -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NeuWell {
                        Text(
                            text = "This file could not be read",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeuError
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = editor.loadError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeuTextSecondary
                        )
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    val pills = infoPills(editor.info, editor.item.sizeBytes)
                    if (pills.isNotEmpty()) {
                        NeuCard(modifier = Modifier.fillMaxWidth()) {
                            SectionLabel("Audio stream (never re-encoded)")
                            Spacer(Modifier.height(10.dp))
                            PillRow(pills = pills)
                        }
                    }

                    CoverArtPicker(
                        cover = editor.shownCover,
                        changed = editor.coverChanged,
                        maxPx = coverMaxPx,
                        keepOriginal = keepOriginalCover,
                        onImagePicked = onPickCover,
                        onRemove = onRemoveCover,
                        onUndo = onUndoCover,
                        onMaxPxChange = onMaxPxChange,
                        onKeepOriginalChange = onKeepOriginalChange,
                        onExport = onExportCover,
                        modifier = Modifier.fillMaxWidth()
                    )

                    NeuTextField(
                        value = editor.edited.title,
                        onValueChange = { onEdit(TagKey.TITLE, it) },
                        label = "Title",
                        capitalization = KeyboardCapitalization.Words
                    )
                    NeuTextField(
                        value = editor.edited.artist,
                        onValueChange = { onEdit(TagKey.ARTIST, it) },
                        label = "Artist",
                        capitalization = KeyboardCapitalization.Words
                    )
                    NeuTextField(
                        value = editor.edited.album,
                        onValueChange = { onEdit(TagKey.ALBUM, it) },
                        label = "Album",
                        capitalization = KeyboardCapitalization.Words
                    )
                    NeuTextField(
                        value = editor.edited.albumArtist,
                        onValueChange = { onEdit(TagKey.ALBUM_ARTIST, it) },
                        label = "Album artist",
                        capitalization = KeyboardCapitalization.Words
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        NeuTextField(
                            value = editor.edited.year,
                            onValueChange = { onEdit(TagKey.YEAR, it) },
                            label = "Year",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number
                        )
                        NeuTextField(
                            value = editor.edited.genre,
                            onValueChange = { onEdit(TagKey.GENRE, it) },
                            label = "Genre",
                            modifier = Modifier.weight(1f),
                            capitalization = KeyboardCapitalization.Words
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        NeuTextField(
                            value = editor.edited.track,
                            onValueChange = { onEdit(TagKey.TRACK, it) },
                            label = "Track number",
                            modifier = Modifier.weight(1f),
                            hint = "3 or 3/12",
                            capitalization = KeyboardCapitalization.None
                        )
                        NeuTextField(
                            value = editor.edited.disc,
                            onValueChange = { onEdit(TagKey.DISC, it) },
                            label = "Disc number",
                            modifier = Modifier.weight(1f),
                            hint = "1 or 1/2",
                            capitalization = KeyboardCapitalization.None
                        )
                    }
                    NeuTextField(
                        value = editor.edited.composer,
                        onValueChange = { onEdit(TagKey.COMPOSER, it) },
                        label = "Composer",
                        capitalization = KeyboardCapitalization.Words
                    )
                    NeuTextField(
                        value = editor.edited.comment,
                        onValueChange = { onEdit(TagKey.COMMENT, it) },
                        label = "Comment",
                        singleLine = false,
                        minLines = 2,
                        imeAction = ImeAction.Default
                    )
                    NeuTextField(
                        value = editor.edited.lyrics,
                        onValueChange = { onEdit(TagKey.LYRICS, it) },
                        label = "Lyrics",
                        singleLine = false,
                        minLines = 5,
                        imeAction = ImeAction.Default
                    )

                    NeuButton(
                        text = "Rename file from tags",
                        onClick = onRenameFromTags,
                        modifier = Modifier.fillMaxWidth(),
                        icon = NeuIcons.Edit,
                        enabled = !editor.dirty
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    NeuButton(
                        text = "Revert",
                        onClick = onRevert,
                        modifier = Modifier.weight(1f),
                        icon = NeuIcons.Undo,
                        enabled = editor.dirty
                    )
                    NeuButton(
                        text = "Save tags",
                        onClick = onSave,
                        modifier = Modifier.weight(1.4f),
                        icon = NeuIcons.Save,
                        primary = true,
                        enabled = editor.dirty
                    )
                }
            }
        }
    }
}
