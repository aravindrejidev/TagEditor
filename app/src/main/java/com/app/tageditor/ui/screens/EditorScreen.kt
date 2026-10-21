package com.app.tageditor.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.app.tageditor.data.TagKey
import com.app.tageditor.ui.components.GenrePickerDialog
import com.app.tageditor.ui.components.NeuButton
import com.app.tageditor.ui.components.NeuCard
import com.app.tageditor.ui.components.NeuIconButton
import com.app.tageditor.ui.components.NeuSwitchRow
import com.app.tageditor.ui.components.NeuTextField
import com.app.tageditor.ui.components.NeuTopBar
import com.app.tageditor.ui.components.NeuWell
import com.app.tageditor.ui.components.PillRow
import com.app.tageditor.ui.components.SectionLabel
import com.app.tageditor.ui.components.SkeletonBlock
import com.app.tageditor.ui.infoPills
import com.app.tageditor.ui.theme.Motion
import com.app.tageditor.ui.theme.NeuError
import com.app.tageditor.ui.theme.NeuIcons
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.ui.theme.staggeredEntrance
import com.app.tageditor.viewmodel.EditorState

/**
 * Shows every tag that is currently stored in the song, pre-filled, and lets the user change
 * whatever is needed. Sections glide in one after another, and the Save bar only appears (with a
 * spring) once there is something to save.
 */
@Composable
fun EditorScreen(
    editor: EditorState,
    coverMaxPx: Int,
    keepOriginalCover: Boolean,
    onBack: () -> Unit,
    onEdit: (TagKey, String) -> Unit,
    onSave: () -> Unit,
    onRevert: () -> Unit,
    onRenameAsTitle: (Boolean) -> Unit,
    onRenameFromTags: () -> Unit,
    onPickCover: (Uri) -> Unit,
    onRemoveCover: () -> Unit,
    onUndoCover: () -> Unit,
    onExportCover: (Uri) -> Unit,
    onMaxPxChange: (Int) -> Unit,
    onKeepOriginalChange: (Boolean) -> Unit
) {
    val showGenres = remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        NeuTopBar(
            title = editor.item.name,
            subtitle = if (editor.dirty) "Unsaved changes" else "Tag editor",
            onBack = onBack
        )

        when {
            editor.loading -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    SkeletonBlock(height = 120.dp, corner = 24.dp)
                    SkeletonBlock(height = 300.dp, corner = 24.dp)
                    SkeletonBlock(height = 58.dp, corner = 16.dp)
                    SkeletonBlock(height = 58.dp, corner = 16.dp)
                    SkeletonBlock(height = 58.dp, corner = 16.dp)
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
                    NeuWell(modifier = Modifier.staggeredEntrance(0)) {
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
                    NeuCard(modifier = Modifier.fillMaxWidth().staggeredEntrance(0)) {
                        SectionLabel("Location")
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Text(
                                text = editor.path.ifEmpty { editor.item.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeuTextSecondary
                            )
                        }
                        val pills = infoPills(editor.info, editor.item.sizeBytes)
                        if (pills.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
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
                        modifier = Modifier.fillMaxWidth().staggeredEntrance(1)
                    )

                    NeuTextField(
                        value = editor.edited.title,
                        onValueChange = { onEdit(TagKey.TITLE, it) },
                        label = "Title",
                        modifier = Modifier.staggeredEntrance(2),
                        capitalization = KeyboardCapitalization.Words
                    )
                    NeuCard(modifier = Modifier.fillMaxWidth().staggeredEntrance(3)) {
                        NeuSwitchRow(
                            title = "Rename file as title",
                            subtitle = "After saving, the file is named after the title",
                            checked = editor.renameAsTitle,
                            onCheckedChange = onRenameAsTitle
                        )
                    }
                    NeuTextField(
                        value = editor.edited.artist,
                        onValueChange = { onEdit(TagKey.ARTIST, it) },
                        label = "Artist",
                        modifier = Modifier.staggeredEntrance(4),
                        capitalization = KeyboardCapitalization.Words
                    )
                    NeuTextField(
                        value = editor.edited.album,
                        onValueChange = { onEdit(TagKey.ALBUM, it) },
                        label = "Album",
                        modifier = Modifier.staggeredEntrance(5),
                        capitalization = KeyboardCapitalization.Words
                    )
                    NeuTextField(
                        value = editor.edited.albumArtist,
                        onValueChange = { onEdit(TagKey.ALBUM_ARTIST, it) },
                        label = "Album artist",
                        modifier = Modifier.staggeredEntrance(6),
                        capitalization = KeyboardCapitalization.Words
                    )
                    Row(
                        modifier = Modifier.staggeredEntrance(7),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NeuTextField(
                            value = editor.edited.year,
                            onValueChange = { onEdit(TagKey.YEAR, it) },
                            label = "Year",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number
                        )
                        NeuTextField(
                            value = editor.edited.track,
                            onValueChange = { onEdit(TagKey.TRACK, it) },
                            label = "Track number",
                            modifier = Modifier.weight(1f),
                            hint = "3 or 3/12",
                            capitalization = KeyboardCapitalization.None
                        )
                    }
                    Row(
                        modifier = Modifier.staggeredEntrance(8),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NeuTextField(
                            value = editor.edited.disc,
                            onValueChange = { onEdit(TagKey.DISC, it) },
                            label = "Disc number",
                            modifier = Modifier.weight(1f),
                            hint = "1 or 1/2",
                            capitalization = KeyboardCapitalization.None
                        )
                        NeuTextField(
                            value = editor.edited.bpm,
                            onValueChange = { onEdit(TagKey.BPM, it) },
                            label = "BPM",
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number
                        )
                    }
                    Row(
                        modifier = Modifier.staggeredEntrance(9),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        NeuTextField(
                            value = editor.edited.genre,
                            onValueChange = { onEdit(TagKey.GENRE, it) },
                            label = "Genre",
                            modifier = Modifier.weight(1f),
                            capitalization = KeyboardCapitalization.Words
                        )
                        Spacer(Modifier.width(12.dp))
                        NeuIconButton(
                            icon = NeuIcons.List,
                            contentDescription = "Choose genre",
                            onClick = { showGenres.value = true }
                        )
                    }
                    Row(
                        modifier = Modifier.staggeredEntrance(10),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        NeuTextField(
                            value = editor.edited.key,
                            onValueChange = { onEdit(TagKey.KEY, it) },
                            label = "Key",
                            modifier = Modifier.weight(1f),
                            hint = "e.g. Am",
                            capitalization = KeyboardCapitalization.None
                        )
                        NeuTextField(
                            value = editor.edited.language,
                            onValueChange = { onEdit(TagKey.LANGUAGE, it) },
                            label = "Language",
                            modifier = Modifier.weight(1f),
                            hint = "e.g. mal",
                            capitalization = KeyboardCapitalization.None
                        )
                    }
                    NeuTextField(
                        value = editor.edited.composer,
                        onValueChange = { onEdit(TagKey.COMPOSER, it) },
                        label = "Composer",
                        modifier = Modifier.staggeredEntrance(11),
                        capitalization = KeyboardCapitalization.Words
                    )
                    NeuTextField(
                        value = editor.edited.lyricist,
                        onValueChange = { onEdit(TagKey.LYRICIST, it) },
                        label = "Lyricist",
                        modifier = Modifier.staggeredEntrance(12),
                        capitalization = KeyboardCapitalization.Words
                    )
                    NeuTextField(
                        value = editor.edited.encoder,
                        onValueChange = { onEdit(TagKey.ENCODER, it) },
                        label = "Encoder",
                        modifier = Modifier.staggeredEntrance(13),
                        capitalization = KeyboardCapitalization.None
                    )
                    NeuTextField(
                        value = editor.edited.comment,
                        onValueChange = { onEdit(TagKey.COMMENT, it) },
                        label = "Comment",
                        modifier = Modifier.staggeredEntrance(14),
                        singleLine = false,
                        minLines = 2,
                        imeAction = ImeAction.Default
                    )
                    NeuTextField(
                        value = editor.edited.lyrics,
                        onValueChange = { onEdit(TagKey.LYRICS, it) },
                        label = "Lyrics",
                        modifier = Modifier.staggeredEntrance(14),
                        singleLine = false,
                        minLines = 6,
                        imeAction = ImeAction.Default
                    )

                    NeuButton(
                        text = "Rename file from tags",
                        onClick = onRenameFromTags,
                        modifier = Modifier.fillMaxWidth().staggeredEntrance(14),
                        icon = NeuIcons.Edit,
                        enabled = !editor.dirty
                    )
                    Spacer(Modifier.height(8.dp))
                }

                AnimatedVisibility(
                    visible = editor.canSave,
                    enter = fadeIn(tween(Motion.Medium)) + expandVertically(
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)
                    ),
                    exit = fadeOut(tween(Motion.Quick)) + shrinkVertically(
                        animationSpec = tween(Motion.Medium)
                    )
                ) {
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
                            text = "Save",
                            onClick = onSave,
                            modifier = Modifier.weight(1.4f),
                            icon = NeuIcons.Save,
                            primary = true,
                            enabled = editor.canSave
                        )
                    }
                }
            }
        }
    }

    if (showGenres.value) {
        GenrePickerDialog(
            onPick = { genre ->
                onEdit(TagKey.GENRE, genre)
                showGenres.value = false
            },
            onDismiss = { showGenres.value = false }
        )
    }
}
