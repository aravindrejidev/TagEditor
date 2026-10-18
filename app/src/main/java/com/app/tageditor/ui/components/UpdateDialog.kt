package com.app.tageditor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.app.tageditor.ui.formatSize
import com.app.tageditor.ui.theme.NeuAccent
import com.app.tageditor.ui.theme.NeuError
import com.app.tageditor.ui.theme.NeuIcons
import com.app.tageditor.ui.theme.NeuOnAccent
import com.app.tageditor.ui.theme.NeuTextPrimary
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.ui.theme.neuInset
import com.app.tageditor.ui.theme.neuRaised
import com.app.tageditor.update.UpdateInfo
import com.app.tageditor.update.UpdatePhase
import com.app.tageditor.update.UpdateUiState
import com.app.tageditor.update.friendlyDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Small chip showing the installed version. Tapping it checks for updates right now. */
@Composable
fun VersionChip(
    version: String,
    checking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeuChip(
        text = if (checking) "Checking ..." else "v$version",
        onClick = onClick,
        modifier = modifier,
        enabled = !checking
    )
}

/** Pop-up shown when a newer release exists on GitHub. */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    currentVersion: String,
    onPrimary: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onOpenPage: () -> Unit
) {
    val info = state.info
    if (state.phase == UpdatePhase.IDLE || info == null) return

    Dialog(
        onDismissRequest = { if (state.phase != UpdatePhase.DOWNLOADING) onLater() },
        properties = DialogProperties(
            dismissOnBackPress = state.phase != UpdatePhase.DOWNLOADING,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        UpdateCard(
            state = state,
            info = info,
            currentVersion = currentVersion,
            onPrimary = onPrimary,
            onLater = onLater,
            onSkip = onSkip,
            onOpenPage = onOpenPage
        )
    }
}

@Composable
private fun UpdateCard(
    state: UpdateUiState,
    info: UpdateInfo,
    currentVersion: String,
    onPrimary: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onOpenPage: () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scope = rememberCoroutineScope()

    // Plays the exit animation first, then runs the action.
    fun closeWith(action: () -> Unit) {
        scope.launch {
            shown = false
            delay(240)
            action()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "cardScale"
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "cardFade"
    )
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

    Box(
        modifier = Modifier
            .padding(horizontal = 22.dp)
            .widthIn(max = 420.dp)
            .heightIn(max = maxHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = fade
                translationY = (1f - fade) * 60.dp.toPx()
            }
            .neuRaised(corner = 32.dp, elevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Reveal(visible = shown, delayMillis = 0) {
                PulseBadge(phase = state.phase)
            }
            Spacer(Modifier.height(12.dp))

            Reveal(visible = shown, delayMillis = 110) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = headline(state.phase),
                        style = MaterialTheme.typography.titleLarge,
                        color = NeuTextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subline(state, info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.phase == UpdatePhase.FAILED) NeuError else NeuTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Reveal(visible = shown, delayMillis = 210) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VersionPill(text = "v$currentVersion", highlighted = false)
                    AnimatedArrow()
                    VersionPill(text = "v${info.version}", highlighted = true)
                }
            }
            Spacer(Modifier.height(16.dp))

            Reveal(visible = shown, delayMillis = 300) {
                val facts = ArrayList<Pair<String, Boolean>>()
                if (info.apkSize > 0) facts.add(Pair(formatSize(info.apkSize), false))
                if (info.publishedAt.isNotBlank()) facts.add(Pair(friendlyDate(info.publishedAt), false))
                facts.add(Pair("Latest release", true))
                PillRow(pills = facts, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(18.dp))

            if (state.phase == UpdatePhase.DOWNLOADING) {
                DownloadProgress(downloaded = state.downloaded, total = state.total)
            } else if (state.phase == UpdatePhase.AVAILABLE) {
                Reveal(visible = shown, delayMillis = 390) {
                    NotesWell(title = info.title, notes = info.notes)
                }
            }
            Spacer(Modifier.height(20.dp))

            Reveal(visible = shown, delayMillis = 480) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.phase != UpdatePhase.DOWNLOADING) {
                        NeuButton(
                            text = primaryLabel(state.phase),
                            onClick = onPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            icon = NeuIcons.Download,
                            primary = true
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    NeuButton(
                        text = if (state.phase == UpdatePhase.DOWNLOADING) "Cancel" else "Later",
                        onClick = { closeWith(onLater) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.phase == UpdatePhase.AVAILABLE) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NeuChip(text = "Skip this version", onClick = { closeWith(onSkip) })
                            NeuChip(text = "Release page", onClick = onOpenPage)
                        }
                    }
                }
            }
        }
    }
}

private fun headline(phase: UpdatePhase): String = when (phase) {
    UpdatePhase.AVAILABLE -> "Update available"
    UpdatePhase.DOWNLOADING -> "Downloading update"
    UpdatePhase.READY -> "Ready to install"
    UpdatePhase.NEEDS_PERMISSION -> "One more step"
    UpdatePhase.FAILED -> "Update failed"
    UpdatePhase.IDLE -> ""
}

private fun subline(state: UpdateUiState, info: UpdateInfo): String = when (state.phase) {
    UpdatePhase.AVAILABLE -> "Tag Editor ${info.version} is ready to download"
    UpdatePhase.DOWNLOADING -> "Keep the app open until it finishes"
    UpdatePhase.READY -> "Tap Install if the installer did not open"
    UpdatePhase.NEEDS_PERMISSION -> "Allow Tag Editor to install apps, come back and tap Install"
    UpdatePhase.FAILED -> state.message ?: "Something went wrong"
    UpdatePhase.IDLE -> ""
}

private fun primaryLabel(phase: UpdatePhase): String = when (phase) {
    UpdatePhase.AVAILABLE -> "Update now"
    UpdatePhase.READY, UpdatePhase.NEEDS_PERMISSION -> "Install"
    UpdatePhase.FAILED -> "Try again"
    UpdatePhase.DOWNLOADING, UpdatePhase.IDLE -> ""
}

/** Fades and slides a block in, [delayMillis] after the card appeared (staggered reveal). */
@Composable
private fun Reveal(visible: Boolean, delayMillis: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 360, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(
                    durationMillis = 460,
                    delayMillis = delayMillis,
                    easing = FastOutSlowInEasing
                ),
                initialOffsetY = { it / 3 }
            )
    ) {
        content()
    }
}

/** Accent badge with two sound-wave rings expanding behind it and a bobbing arrow. */
@Composable
private fun PulseBadge(phase: UpdatePhase) {
    val transition = rememberInfiniteTransition(label = "badge")
    val ringOne by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringOne"
    )
    val ringTwo by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1100)
        ),
        label = "ringTwo"
    )
    val bob by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )
    val tint = if (phase == UpdatePhase.FAILED) NeuError else NeuAccent

    Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.28f), Color.Transparent),
                    radius = outer
                ),
                radius = outer
            )
            for (progress in listOf(ringOne, ringTwo)) {
                drawCircle(
                    color = tint.copy(alpha = (1f - progress) * 0.45f),
                    radius = outer * (0.5f + 0.5f * progress),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        Box(
            modifier = Modifier
                .size(66.dp)
                .neuRaised(corner = 33.dp, elevation = 6.dp, body = tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (phase == UpdatePhase.READY) NeuIcons.Check else NeuIcons.Download,
                contentDescription = null,
                tint = NeuOnAccent,
                modifier = Modifier
                    .size(30.dp)
                    .offset(y = if (phase == UpdatePhase.READY) 0.dp else bob.dp)
            )
        }
    }
}

@Composable
private fun VersionPill(text: String, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .neuInset(corner = 14.dp, depth = 2.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlighted) NeuAccent else NeuTextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun AnimatedArrow() {
    val transition = rememberInfiniteTransition(label = "arrow")
    val shift by transition.animateFloat(
        initialValue = -3f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowShift"
    )
    Text(
        text = "\u2192",
        style = MaterialTheme.typography.titleLarge,
        color = NeuAccent,
        modifier = Modifier.offset(x = shift.dp)
    )
}

@Composable
private fun DownloadProgress(downloaded: Long, total: Long) {
    val target = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 300),
        label = "downloadProgress"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        NeuProgressBar(progress = if (total > 0) animated else null)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (total > 0) "${formatSize(downloaded)} of ${formatSize(total)}" else formatSize(downloaded),
                style = MaterialTheme.typography.labelMedium,
                color = NeuTextSecondary,
                modifier = Modifier.weight(1f)
            )
            if (total > 0) {
                Text(
                    text = "${(animated * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeuAccent
                )
            }
        }
    }
}

/** "What's new": the release message from GitHub, shown in a sunken panel. */
@Composable
private fun NotesWell(title: String, notes: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neuInset(corner = 20.dp, depth = 4.dp)
            .padding(16.dp)
    ) {
        Text(
            text = "WHAT'S NEW",
            style = MaterialTheme.typography.labelSmall,
            color = NeuTextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = NeuTextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .heightIn(max = 150.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val lines = remember(notes) { notes.lines() }
            if (notes.isBlank()) {
                Text(
                    text = "Bug fixes and improvements.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuTextSecondary
                )
            }
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    Text(
                        text = plain(line.trimStart('#')),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeuTextPrimary
                    )
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    Row {
                        Text(
                            text = "\u2022",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeuAccent
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = plain(line.substring(2)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeuTextSecondary
                        )
                    }
                } else {
                    Text(
                        text = plain(line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeuTextSecondary
                    )
                }
            }
        }
    }
}

private val markdownLink = Regex("\\[([^\\]]+)\\]\\([^)]*\\)")

/** Strips the most common Markdown decoration so release notes read cleanly as plain text. */
private fun plain(markdown: String): String {
    return markdownLink.replace(markdown, "\$1")
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .trim()
}
