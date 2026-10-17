package com.app.tageditor.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.tageditor.ui.components.BusyOverlay
import com.app.tageditor.ui.components.NeuDialog
import com.app.tageditor.ui.components.OpenAudioDocuments
import com.app.tageditor.ui.components.OpenFolder
import com.app.tageditor.ui.screens.BatchScreen
import com.app.tageditor.ui.screens.EditorScreen
import com.app.tageditor.ui.screens.HomeScreen
import com.app.tageditor.ui.screens.RenameScreen
import com.app.tageditor.ui.theme.NeuBackground
import com.app.tageditor.ui.theme.NeuAccent
import com.app.tageditor.ui.theme.NeuShapes
import com.app.tageditor.ui.theme.NeuSurface
import com.app.tageditor.ui.theme.NeuTextPrimary
import com.app.tageditor.ui.theme.NeuTextSecondary
import com.app.tageditor.viewmodel.Screen
import com.app.tageditor.viewmodel.TagEditorViewModel

/** Root composable: owns the file pickers, routes between screens and shows global overlays. */
@Composable
fun TagEditorApp(vm: TagEditorViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    val openFiles = rememberLauncherForActivityResult(remember { OpenAudioDocuments() }) { uris ->
        vm.onFilesPicked(uris)
    }
    val openFolder = rememberLauncherForActivityResult(remember { OpenFolder() }) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }

    LaunchedEffect(vm) {
        vm.messages.collect { message -> snackbarHost.showSnackbar(message) }
    }

    BackHandler(enabled = state.screen != Screen.HOME && state.busy == null) {
        focusManager.clearFocus()
        vm.back()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeuBackground)
    ) {
        Crossfade(
            targetState = state.screen,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            animationSpec = tween(durationMillis = 180),
            label = "screen"
        ) { screen ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                ) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            items = state.items,
                            selected = state.selected,
                            revision = state.revision,
                            onOpenFiles = { openFiles.launch(Unit) },
                            onOpenFolder = { openFolder.launch(Unit) },
                            onItemClick = vm::openEditor,
                            onToggle = vm::toggleSelected,
                            onSelectAll = vm::selectAll,
                            onClearSelection = vm::clearSelection,
                            onClearList = vm::clearList,
                            onBatch = vm::openBatch,
                            onRename = vm::openRename,
                            loadTags = { item -> vm.tagsFor(item) }
                        )

                        Screen.EDITOR -> {
                            val editor = state.editor
                            if (editor != null) {
                                EditorScreen(
                                    editor = editor,
                                    coverMaxPx = state.coverMaxPx,
                                    keepOriginalCover = state.keepOriginalCover,
                                    onBack = vm::back,
                                    onEdit = vm::editField,
                                    onSave = vm::saveEditor,
                                    onRevert = vm::revertEditor,
                                    onRenameAsTitle = vm::setRenameAsTitle,
                                    onRenameFromTags = vm::openRenameForEditor,
                                    onPickCover = vm::pickEditorCover,
                                    onRemoveCover = vm::removeEditorCover,
                                    onUndoCover = vm::resetEditorCover,
                                    onExportCover = vm::exportCover,
                                    onMaxPxChange = vm::setCoverMaxPx,
                                    onKeepOriginalChange = vm::setKeepOriginalCover
                                )
                            }
                        }

                        Screen.BATCH -> BatchScreen(
                            fileCount = state.selected.size,
                            draft = state.batch,
                            coverMaxPx = state.coverMaxPx,
                            keepOriginalCover = state.keepOriginalCover,
                            onBack = vm::back,
                            onFieldChange = vm::editBatchField,
                            onNumberTracks = vm::setNumberTracks,
                            onPickCover = vm::pickBatchCover,
                            onCoverMode = vm::setBatchCoverMode,
                            onMaxPxChange = vm::setCoverMaxPx,
                            onKeepOriginalChange = vm::setKeepOriginalCover,
                            onApply = vm::applyBatch
                        )

                        Screen.RENAME -> RenameScreen(
                            draft = state.rename,
                            onBack = vm::back,
                            onTemplateChange = vm::setTemplate,
                            onApply = vm::applyRename
                        )
                    }
                }
            }
        }

        val busy = state.busy
        if (busy != null) {
            BusyOverlay(busy = busy)
        }

        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(16.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = NeuSurface,
                contentColor = NeuTextPrimary,
                actionColor = NeuAccent,
                shape = NeuShapes.Medium
            )
        }

        if (state.confirmDiscard) {
            NeuDialog(
                title = "Discard changes?",
                confirmLabel = "Discard",
                onConfirm = vm::discardAndClose,
                onDismiss = vm::dismissDiscard,
                dismissLabel = "Keep editing"
            ) {
                Text(
                    text = "Your edits to this file have not been saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeuTextSecondary
                )
            }
        }

        val report = state.report
        if (report != null) {
            NeuDialog(
                title = report.title,
                confirmLabel = "OK",
                onConfirm = vm::dismissReport,
                onDismiss = vm::dismissReport
            ) {
                Column {
                    Text(
                        text = report.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeuTextPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        for (line in report.details) {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelMedium,
                                color = NeuTextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
