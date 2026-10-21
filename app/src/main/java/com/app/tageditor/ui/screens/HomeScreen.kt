package com.app.tageditor.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.tageditor.data.AudioItem
import com.app.tageditor.data.TagData
import com.app.tageditor.ui.components.NeuButton
import com.app.tageditor.ui.components.NeuCard
import com.app.tageditor.ui.components.NeuCheckbox
import com.app.tageditor.ui.components.NeuChip
import com.app.tageditor.ui.components.NeuIconButton
import com.app.tageditor.ui.formatSize
import com.app.tageditor.ui.theme.Motion
import com.app.tageditor.ui.theme.NeuAccent
import com.app.tageditor.ui.theme.NeuIcons
import com.app.tageditor.ui.theme.NeuTextHint
import com.app.tageditor.ui.theme.NeuTextPrimary
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.ui.theme.neuInset
import com.app.tageditor.ui.theme.neuRaised
import com.app.tageditor.ui.theme.staggeredEntrance
import kotlinx.coroutines.CancellationException

/**
 * File list: pick files or a folder, tap a song to edit it, tick songs for batch actions.
 * [listState] and [seenRows] live in the root so the scroll position survives opening an editor
 * and rows only play their entrance animation the first time they appear.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    items: List<AudioItem>,
    selected: Set<Uri>,
    revision: Int,
    listState: LazyListState,
    seenRows: MutableSet<String>,
    onOpenFiles: () -> Unit,
    onOpenFolder: () -> Unit,
    onItemClick: (AudioItem) -> Unit,
    onToggle: (Uri) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onClearList: () -> Unit,
    onBatch: () -> Unit,
    onRename: () -> Unit,
    loadTags: suspend (AudioItem) -> TagData
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tag Editor",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeuTextPrimary
                )
                Text(
                    text = "Edit tags in MP3, FLAC and M4A files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuTextSecondary
                )
            }
            AnimatedVisibility(
                visible = items.isNotEmpty(),
                enter = fadeIn(tween(Motion.Medium)),
                exit = fadeOut(tween(Motion.Quick))
            ) {
                NeuIconButton(
                    icon = NeuIcons.Close,
                    contentDescription = "Clear file list",
                    onClick = onClearList
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NeuButton(
                text = "Open files",
                onClick = onOpenFiles,
                modifier = Modifier.weight(1f),
                icon = NeuIcons.MusicNote,
                primary = true
            )
            NeuButton(
                text = "Open folder",
                onClick = onOpenFolder,
                modifier = Modifier.weight(1f),
                icon = NeuIcons.Folder
            )
        }

        if (items.isEmpty()) {
            EmptyState(modifier = Modifier.weight(1f))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selected.isEmpty()) {
                        "${items.size} files"
                    } else {
                        "${selected.size} of ${items.size} selected"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected.isEmpty()) NeuTextSecondary else NeuAccent,
                    modifier = Modifier.weight(1f)
                )
                NeuChip(text = "Select all", onClick = onSelectAll)
                Spacer(Modifier.width(10.dp))
                NeuChip(text = "None", onClick = onClearSelection, enabled = selected.isNotEmpty())
            }

            Text(
                text = "Tap a song to edit it. Tick several songs to edit them together.",
                style = MaterialTheme.typography.labelMedium,
                color = NeuTextHint,
                modifier = Modifier.padding(horizontal = 26.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(items = items, key = { _, item -> item.uri.toString() }) { index, item ->
                    AudioRow(
                        item = item,
                        index = index,
                        animateIn = remember(item.uri) { seenRows.add(item.uri.toString()) && index < 12 },
                        checked = item.uri in selected,
                        revision = revision,
                        onClick = { onItemClick(item) },
                        onToggle = { onToggle(item.uri) },
                        loadTags = loadTags,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selected.isNotEmpty(),
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                initialOffsetY = { it }
            ) + fadeIn(tween(Motion.Medium)),
            exit = slideOutVertically(
                animationSpec = tween(Motion.Medium, easing = Motion.Emphasized),
                targetOffsetY = { it }
            ) + fadeOut(tween(Motion.Quick))
        ) {
            SelectionBar(count = selected.size, onBatch = onBatch, onRename = onRename)
        }
    }
}

@Composable
private fun AudioRow(
    item: AudioItem,
    index: Int,
    animateIn: Boolean,
    checked: Boolean,
    revision: Int,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    loadTags: suspend (AudioItem) -> TagData,
    modifier: Modifier = Modifier
) {
    val tags by produceState<TagData?>(null, item.uri, revision) {
        value = try {
            loadTags(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    val selectGlow by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(Motion.Medium),
        label = "rowSelect"
    )
    val title = tags?.title?.takeIf { it.isNotBlank() } ?: item.baseName
    val artist = tags?.artist.orEmpty()
    val album = tags?.album.orEmpty()
    val subtitle = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" - ")
    val detail = listOf(item.extension.uppercase(), formatSize(item.sizeBytes), tags?.year.orEmpty(), item.folder)
        .filter { it.isNotBlank() }
        .joinToString("   ")

    Row(
        modifier = modifier
            .then(if (animateIn) Modifier.staggeredEntrance(index) else Modifier)
            .fillMaxWidth()
            .neuRaised(corner = 20.dp, elevation = 5.dp)
            .border(1.5.dp, NeuAccent.copy(alpha = selectGlow * 0.75f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NeuCheckbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NeuTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (subtitle.isEmpty() && title != item.name) item.name else detail,
                style = MaterialTheme.typography.labelMedium,
                color = NeuTextHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onBatch: () -> Unit, onRename: () -> Unit) {
    NeuCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        corner = 26.dp,
        elevation = 8.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = if (count == 1) "1 file selected" else "$count files selected",
            style = MaterialTheme.typography.labelMedium,
            color = NeuTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeuButton(
                text = if (count == 1) "Edit tags" else "Batch edit",
                onClick = onBatch,
                modifier = Modifier.weight(1f),
                icon = NeuIcons.Tune,
                primary = true
            )
            NeuButton(
                text = "Rename",
                onClick = onRename,
                modifier = Modifier.weight(1f),
                icon = NeuIcons.Edit
            )
        }
    }
}

/** Friendly empty state: a floating note with a soft ring that keeps radiating. */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "empty")
    val bob by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyBob"
    )
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "emptyRing"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp)
            .staggeredEntrance(1),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val outer = size.minDimension / 2f
                    drawCircle(
                        color = NeuAccent.copy(alpha = (1f - ring) * 0.3f),
                        radius = outer * (0.55f + 0.45f * ring),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .offset(y = bob.dp)
                        .neuInset(corner = 48.dp, depth = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = NeuIcons.MusicNote,
                        contentDescription = null,
                        tint = NeuAccent,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "No files yet",
                style = MaterialTheme.typography.titleMedium,
                color = NeuTextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Open audio files or a whole album folder to start editing. Files stay on your device and are edited in place.",
                style = MaterialTheme.typography.bodyMedium,
                color = NeuTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
