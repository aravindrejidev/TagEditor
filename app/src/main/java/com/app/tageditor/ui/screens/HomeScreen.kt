package com.app.tageditor.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.tageditor.data.AudioItem
import com.app.tageditor.data.FolderEntry
import com.app.tageditor.data.TagData
import com.app.tageditor.ui.components.NeuButton
import com.app.tageditor.ui.components.NeuCard
import com.app.tageditor.ui.components.NeuCheckbox
import com.app.tageditor.ui.components.ChipFlow
import com.app.tageditor.ui.components.NeuChip
import com.app.tageditor.ui.components.NeuIconButton
import com.app.tageditor.ui.components.NeuTextField
import com.app.tageditor.ui.components.SkeletonBlock
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
import com.app.tageditor.viewmodel.BrowserState
import com.app.tageditor.viewmodel.HomeTab
import kotlinx.coroutines.CancellationException

/**
 * Home: two ways to find songs. **All** lists every song on the phone (with cover art and a
 * search box); **Browse** is a built-in file browser. Tap a song to edit it, tick several songs
 * for batch actions. The list states and [seenRows] live in the root so the scroll position
 * survives opening an editor and rows only play their entrance animation the first time.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    tab: HomeTab,
    hasAccess: Boolean,
    items: List<AudioItem>,
    selected: Set<Uri>,
    revision: Int,
    libraryLoading: Boolean,
    browser: BrowserState,
    allListState: LazyListState,
    browseListState: LazyListState,
    seenRows: MutableSet<String>,
    onTab: (HomeTab) -> Unit,
    onAllowAccess: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onUp: () -> Unit,
    onItemClick: (AudioItem) -> Unit,
    onToggle: (Uri) -> Unit,
    onSelect: (List<AudioItem>) -> Unit,
    onClearSelection: () -> Unit,
    onBatch: () -> Unit,
    onRename: () -> Unit,
    loadTags: suspend (AudioItem) -> TagData,
    loadCover: suspend (AudioItem) -> Bitmap?
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
        ) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NeuButton(
                text = "All",
                onClick = { onTab(HomeTab.ALL) },
                modifier = Modifier.weight(1f),
                icon = NeuIcons.MusicNote,
                primary = tab == HomeTab.ALL
            )
            NeuButton(
                text = "Browse",
                onClick = { onTab(HomeTab.BROWSE) },
                modifier = Modifier.weight(1f),
                icon = NeuIcons.Folder,
                primary = tab == HomeTab.BROWSE
            )
        }

        when {
            !hasAccess -> AccessGate(onAllow = onAllowAccess, modifier = Modifier.weight(1f))
            tab == HomeTab.ALL -> AllSongs(
                items = items,
                selected = selected,
                revision = revision,
                loading = libraryLoading,
                listState = allListState,
                seenRows = seenRows,
                onRefresh = onRefresh,
                onItemClick = onItemClick,
                onToggle = onToggle,
                onSelect = onSelect,
                onClearSelection = onClearSelection,
                loadTags = loadTags,
                loadCover = loadCover,
                modifier = Modifier.weight(1f)
            )
            else -> BrowseFiles(
                browser = browser,
                items = items,
                selected = selected,
                revision = revision,
                listState = browseListState,
                seenRows = seenRows,
                onOpenFolder = onOpenFolder,
                onUp = onUp,
                onItemClick = onItemClick,
                onToggle = onToggle,
                onSelect = onSelect,
                onClearSelection = onClearSelection,
                loadTags = loadTags,
                loadCover = loadCover,
                modifier = Modifier.weight(1f)
            )
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

/** The "All" tab: every song on the phone, with a search box and a rescan button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllSongs(
    items: List<AudioItem>,
    selected: Set<Uri>,
    revision: Int,
    loading: Boolean,
    listState: LazyListState,
    seenRows: MutableSet<String>,
    onRefresh: () -> Unit,
    onItemClick: (AudioItem) -> Unit,
    onToggle: (Uri) -> Unit,
    onSelect: (List<AudioItem>) -> Unit,
    onClearSelection: () -> Unit,
    loadTags: suspend (AudioItem) -> TagData,
    loadCover: suspend (AudioItem) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(items, query) { filterSongs(items, query) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeuTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search songs",
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Search,
                capitalization = KeyboardCapitalization.None
            )
            NeuIconButton(
                icon = NeuIcons.Refresh,
                contentDescription = "Rescan library",
                onClick = onRefresh,
                enabled = !loading
            )
        }

        when {
            items.isEmpty() && loading -> LoadingList(modifier = Modifier.weight(1f))
            items.isEmpty() -> EmptyState(
                title = "No songs found",
                text = "Android lists no MP3, FLAC or M4A songs yet. Use Browse to open a folder, or tap the rescan button.",
                modifier = Modifier.weight(1f)
            )
            shown.isEmpty() -> EmptyState(
                title = "No match",
                text = "No song name or folder contains \"${query.trim()}\".",
                modifier = Modifier.weight(1f)
            )
            else -> {
                SelectionRow(
                    total = shown.size,
                    selectedCount = selected.size,
                    onSelectAll = { onSelect(shown) },
                    onNone = onClearSelection
                )
                HintText()
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(items = shown, key = { _, item -> item.uri.toString() }) { index, item ->
                        AudioRow(
                            item = item,
                            index = index,
                            animateIn = remember(item.uri) { seenRows.add(item.uri.toString()) && index < 12 },
                            checked = item.uri in selected,
                            revision = revision,
                            onClick = { onItemClick(item) },
                            onToggle = { onToggle(item.uri) },
                            loadTags = loadTags,
                            loadCover = loadCover,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

private fun filterSongs(items: List<AudioItem>, query: String): List<AudioItem> {
    val q = query.trim()
    if (q.isEmpty()) return items
    return items.filter { it.name.contains(q, ignoreCase = true) || it.folder.contains(q, ignoreCase = true) }
}

/** The "Browse" tab: the built-in file browser. Folders first, then the songs inside. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseFiles(
    browser: BrowserState,
    items: List<AudioItem>,
    selected: Set<Uri>,
    revision: Int,
    listState: LazyListState,
    seenRows: MutableSet<String>,
    onOpenFolder: (String) -> Unit,
    onUp: () -> Unit,
    onItemClick: (AudioItem) -> Unit,
    onToggle: (Uri) -> Unit,
    onSelect: (List<AudioItem>) -> Unit,
    onClearSelection: () -> Unit,
    loadTags: suspend (AudioItem) -> TagData,
    loadCover: suspend (AudioItem) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    // Every folder starts at the top of its list.
    LaunchedEffect(browser.dir) { listState.scrollToItem(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        PathBar(browser = browser, onUp = onUp, onOpenFolder = onOpenFolder)

        val error = browser.error
        when {
            browser.loading -> LoadingList(modifier = Modifier.weight(1f))
            error != null -> EmptyState(
                title = "Can't open this folder",
                text = error,
                modifier = Modifier.weight(1f)
            )
            browser.folders.isEmpty() && items.isEmpty() -> EmptyState(
                title = "Nothing here",
                text = "This folder has no sub-folders and no MP3, FLAC or M4A songs.",
                modifier = Modifier.weight(1f)
            )
            else -> {
                if (items.isNotEmpty()) {
                    SelectionRow(
                        total = items.size,
                        selectedCount = selected.size,
                        onSelectAll = { onSelect(items) },
                        onNone = onClearSelection
                    )
                    HintText()
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(items = browser.folders, key = { "folder:" + it.path }) { folder ->
                        FolderRow(
                            folder = folder,
                            onClick = { onOpenFolder(folder.path) },
                            modifier = Modifier.animateItem()
                        )
                    }
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
                            loadCover = loadCover,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

/** Storage chips (when there is more than one volume), the "up" button and the current folder. */
@Composable
private fun PathBar(
    browser: BrowserState,
    onUp: () -> Unit,
    onOpenFolder: (String) -> Unit
) {
    val root = browser.root
    val title = if (browser.canGoUp) {
        browser.dir.substringAfterLast('/')
    } else {
        root?.label ?: "Storage"
    }
    val location = if (root != null) root.label + browser.dir.removePrefix(root.path) else browser.dir

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (browser.roots.size > 1) {
            ChipFlow {
                for (volume in browser.roots) {
                    NeuChip(text = volume.label, onClick = { onOpenFolder(volume.path) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(
                icon = NeuIcons.Back,
                contentDescription = "Up one folder",
                onClick = onUp,
                enabled = browser.canGoUp
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NeuTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = location,
                    style = MaterialTheme.typography.labelMedium,
                    color = NeuTextHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderEntry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .neuRaised(corner = 20.dp, elevation = 5.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .neuInset(corner = 14.dp, depth = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = NeuIcons.Folder,
                contentDescription = null,
                tint = NeuAccent,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.titleMedium,
            color = NeuTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** "12 songs" or "3 of 12 selected" with Select all / None. */
@Composable
private fun SelectionRow(
    total: Int,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onNone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selectedCount == 0) {
                if (total == 1) "1 song" else "$total songs"
            } else {
                "$selectedCount of $total selected"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (selectedCount == 0) NeuTextSecondary else NeuAccent,
            modifier = Modifier.weight(1f)
        )
        NeuChip(text = "Select all", onClick = onSelectAll)
        Spacer(Modifier.width(10.dp))
        NeuChip(text = "None", onClick = onNone, enabled = selectedCount > 0)
    }
}

@Composable
private fun HintText() {
    Text(
        text = "Tap a song to edit it. Tick several songs to edit them together.",
        style = MaterialTheme.typography.labelMedium,
        color = NeuTextHint,
        modifier = Modifier.padding(horizontal = 26.dp)
    )
}

@Composable
private fun LoadingList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(5) { SkeletonBlock(height = 76.dp) }
    }
}

/** Shown until the audio permission and "All files access" are granted. */
@Composable
private fun AccessGate(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        NeuCard(
            modifier = Modifier
                .fillMaxWidth()
                .staggeredEntrance(1),
            corner = 28.dp,
            elevation = 8.dp,
            contentPadding = PaddingValues(22.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .neuInset(corner = 32.dp, depth = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = NeuIcons.Folder,
                    contentDescription = null,
                    tint = NeuAccent,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Allow access to your music",
                style = MaterialTheme.typography.titleMedium,
                color = NeuTextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tag Editor needs to see the files on your phone to list your songs, let you browse folders and write tags straight into the files. Nothing leaves your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = NeuTextSecondary
            )
            Spacer(Modifier.height(18.dp))
            NeuButton(
                text = "Allow access",
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth(),
                icon = NeuIcons.Check,
                primary = true
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Android asks for audio access first, then opens the \"All files access\" switch. Turn it on for Tag Editor and come back.",
                style = MaterialTheme.typography.labelMedium,
                color = NeuTextHint
            )
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
    loadCover: suspend (AudioItem) -> Bitmap?,
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
    val cover by produceState<ImageBitmap?>(null, item.uri, revision) {
        value = try {
            loadCover(item)?.asImageBitmap()
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
        CoverThumb(cover = cover)
        Spacer(Modifier.width(12.dp))
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

/** Small square cover picture of a song, or a music note when it has no embedded cover. */
@Composable
private fun CoverThumb(cover: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(54.dp)
            .neuInset(corner = 14.dp, depth = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
            )
        } else {
            Icon(
                imageVector = NeuIcons.MusicNote,
                contentDescription = null,
                tint = NeuTextHint,
                modifier = Modifier.size(24.dp)
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
private fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NeuTextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = NeuTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
