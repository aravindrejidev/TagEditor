package com.app.tageditor.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.tageditor.data.AudioFileParser
import com.app.tageditor.data.AudioItem
import com.app.tageditor.data.CoverArt
import com.app.tageditor.data.CoverChange
import com.app.tageditor.data.FileNameTemplate
import com.app.tageditor.data.TagData
import com.app.tageditor.data.TagEditorRepository
import com.app.tageditor.data.TagKey
import com.app.tageditor.data.diffTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileNotFoundException

class TagEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TagEditorRepository(application)
    private val prefs = application.getSharedPreferences("tag_editor_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            rename = RenameDraft(
                template = prefs.getString(PREF_TEMPLATE, null) ?: FileNameTemplate.DEFAULT
            ),
            coverMaxPx = prefs.getInt(PREF_COVER_PX, 1000),
            keepOriginalCover = prefs.getBoolean(PREF_KEEP_ORIGINAL, false)
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val messageChannel = Channel<String>(Channel.BUFFERED)

    /** One-shot messages for the snackbar. */
    val messages: Flow<String> = messageChannel.receiveAsFlow()

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun toast(text: String) {
        messageChannel.trySend(text)
    }

    private fun setBusy(message: String, done: Int = 0, total: Int = 0) {
        _state.update { it.copy(busy = BusyState(message, done, total)) }
    }

    private fun clearBusy() {
        _state.update { it.copy(busy = null) }
    }

    private fun friendly(t: Throwable): String = when (t) {
        is SecurityException ->
            "No write permission for this file. Add it again with \"Open files\" or \"Open folder\"."
        is FileNotFoundException -> "The file was not found or cannot be written."
        is OutOfMemoryError -> "Not enough memory to process this file."
        else -> t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
    }

    /** Suspend-safe item lookup used by the list rows. */
    suspend fun tagsFor(item: AudioItem): TagData = repo.quickTags(item)

    // ---------------------------------------------------------------------------------------
    // Picking files
    // ---------------------------------------------------------------------------------------

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            setBusy("Reading files")
            val result = runCatching { repo.describe(uris) }
            clearBusy()
            result.onSuccess { described ->
                val supported = described.filter { AudioFileParser.isSupported(it.name) }
                val skipped = described.size - supported.size
                addItems(supported)
                when {
                    supported.isEmpty() -> toast("Only MP3, FLAC and M4A files are supported")
                    skipped > 0 -> toast("$skipped unsupported file(s) skipped")
                    else -> Unit
                }
            }.onFailure { toast("Could not read the selection: ${friendly(it)}") }
        }
    }

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            setBusy("Scanning folder")
            val result = runCatching { repo.scanTree(uri, recursive = true) }
            clearBusy()
            result.onSuccess { found ->
                if (found.isEmpty()) {
                    toast("No MP3, FLAC or M4A files found in that folder")
                } else {
                    addItems(found)
                    toast("${found.size} files found")
                }
            }.onFailure { toast("Could not read the folder: ${friendly(it)}") }
        }
    }

    private fun addItems(newItems: List<AudioItem>) {
        _state.update { s ->
            val known = s.items.map { it.uri }.toHashSet()
            val fresh = newItems.filter { known.add(it.uri) }
            s.copy(items = s.items + fresh)
        }
    }

    fun toggleSelected(uri: Uri) {
        _state.update { s ->
            s.copy(selected = if (uri in s.selected) s.selected - uri else s.selected + uri)
        }
    }

    fun selectAll() {
        _state.update { s -> s.copy(selected = s.items.map { it.uri }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    fun clearList() {
        _state.update { it.copy(items = emptyList(), selected = emptySet()) }
    }

    // ---------------------------------------------------------------------------------------
    // Single file editor
    // ---------------------------------------------------------------------------------------

    fun openEditor(item: AudioItem) {
        _state.update { it.copy(screen = Screen.EDITOR, editor = EditorState(item = item)) }
        loadEditor(item)
    }

    private fun loadEditor(item: AudioItem) {
        viewModelScope.launch {
            val result = runCatching { repo.load(item) }
            _state.update { s ->
                val current = s.editor
                if (current == null || current.item.uri != item.uri) {
                    s
                } else {
                    result.fold(
                        onSuccess = { parsed ->
                            s.copy(
                                editor = current.copy(
                                    loading = false,
                                    original = parsed.tags,
                                    edited = parsed.tags,
                                    info = parsed.info,
                                    embeddedCover = parsed.cover,
                                    coverChange = CoverChange.Keep,
                                    loadError = null
                                )
                            )
                        },
                        onFailure = { error ->
                            s.copy(editor = current.copy(loading = false, loadError = friendly(error)))
                        }
                    )
                }
            }
        }
    }

    fun editField(key: TagKey, value: String) {
        _state.update { s ->
            val current = s.editor
            if (current == null) s else s.copy(editor = current.copy(edited = current.edited.withValue(key, value)))
        }
    }

    fun revertEditor() {
        _state.update { s ->
            val current = s.editor
            if (current == null) s else s.copy(
                editor = current.copy(edited = current.original, coverChange = CoverChange.Keep)
            )
        }
    }

    fun saveEditor() {
        val editor = _state.value.editor ?: return
        if (!editor.dirty) return
        val patch = diffTags(editor.original, editor.edited)
        viewModelScope.launch {
            setBusy("Saving tags")
            val saved = runCatching { repo.saveTags(editor.item, patch, editor.coverChange) }
            if (saved.isSuccess) {
                val reloaded = runCatching { repo.load(editor.item) }.getOrNull()
                _state.update { s ->
                    val current = s.editor
                    if (current == null || current.item.uri != editor.item.uri) {
                        s
                    } else {
                        val refreshed = if (reloaded != null) {
                            current.copy(
                                original = reloaded.tags,
                                edited = reloaded.tags,
                                info = reloaded.info,
                                embeddedCover = reloaded.cover,
                                coverChange = CoverChange.Keep
                            )
                        } else {
                            current.copy(
                                original = current.edited,
                                embeddedCover = current.shownCover,
                                coverChange = CoverChange.Keep
                            )
                        }
                        s.copy(editor = refreshed, revision = s.revision + 1)
                    }
                }
                toast("Tags saved")
            } else {
                val error = saved.exceptionOrNull()
                toast("Save failed: ${if (error != null) friendly(error) else "unknown error"}")
            }
            clearBusy()
        }
    }

    // ----- cover art (single file) -----

    fun setCoverMaxPx(px: Int) {
        prefs.edit().putInt(PREF_COVER_PX, px).apply()
        _state.update { it.copy(coverMaxPx = px) }
    }

    fun setKeepOriginalCover(keep: Boolean) {
        prefs.edit().putBoolean(PREF_KEEP_ORIGINAL, keep).apply()
        _state.update { it.copy(keepOriginalCover = keep) }
    }

    private fun prepareCover(uri: Uri, onReady: (CoverArt) -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            setBusy("Preparing image")
            val result = runCatching { repo.prepareCover(uri, s.coverMaxPx, s.keepOriginalCover) }
            clearBusy()
            result.onSuccess(onReady).onFailure { toast("Image error: ${friendly(it)}") }
        }
    }

    fun pickEditorCover(uri: Uri) {
        prepareCover(uri) { art ->
            _state.update { s ->
                val current = s.editor
                if (current == null) s else s.copy(editor = current.copy(coverChange = CoverChange.Replace(art)))
            }
        }
    }

    fun removeEditorCover() {
        _state.update { s ->
            val current = s.editor
            if (current == null) s else s.copy(editor = current.copy(coverChange = CoverChange.Remove))
        }
    }

    fun resetEditorCover() {
        _state.update { s ->
            val current = s.editor
            if (current == null) s else s.copy(editor = current.copy(coverChange = CoverChange.Keep))
        }
    }

    /** Saves the cover that is currently shown to a document created by the user. */
    fun exportCover(target: Uri) {
        val art = _state.value.editor?.shownCover ?: return
        viewModelScope.launch {
            val result = runCatching { repo.writeBytes(target, art.bytes) }
            val error = result.exceptionOrNull()
            toast(if (error == null) "Cover image saved" else "Could not save the image: ${friendly(error)}")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Batch editor
    // ---------------------------------------------------------------------------------------

    fun openBatch() {
        if (_state.value.selected.isEmpty()) {
            toast("Select one or more files first")
            return
        }
        _state.update { it.copy(screen = Screen.BATCH, batch = BatchDraft()) }
    }

    fun editBatchField(key: TagKey, draft: FieldDraft) {
        _state.update { s -> s.copy(batch = s.batch.copy(fields = s.batch.fields + (key to draft))) }
    }

    fun setNumberTracks(enabled: Boolean) {
        _state.update { s -> s.copy(batch = s.batch.copy(numberTracks = enabled)) }
    }

    fun pickBatchCover(uri: Uri) {
        prepareCover(uri) { art ->
            _state.update { s ->
                s.copy(batch = s.batch.copy(coverMode = CoverMode.REPLACE, newCover = art))
            }
        }
    }

    fun setBatchCoverMode(mode: CoverMode) {
        _state.update { s ->
            s.copy(
                batch = s.batch.copy(
                    coverMode = mode,
                    newCover = if (mode == CoverMode.REPLACE) s.batch.newCover else null
                )
            )
        }
    }

    fun applyBatch() {
        val s = _state.value
        val targets = s.items.filter { it.uri in s.selected }
        val draft = s.batch
        if (targets.isEmpty() || !draft.hasChanges) {
            toast("Nothing to apply")
            return
        }
        val cover: CoverChange = when (draft.coverMode) {
            CoverMode.KEEP -> CoverChange.Keep
            CoverMode.REMOVE -> CoverChange.Remove
            CoverMode.REPLACE -> {
                val art = draft.newCover
                if (art != null) CoverChange.Replace(art) else CoverChange.Keep
            }
        }
        viewModelScope.launch {
            val failures = ArrayList<String>()
            var succeeded = 0
            targets.forEachIndexed { index, item ->
                setBusy("Updating ${item.name}", index, targets.size)
                val patch = LinkedHashMap<TagKey, String>()
                for ((key, field) in draft.fields) {
                    if (field.enabled) patch[key] = field.value
                }
                if (draft.numberTracks) patch[TagKey.TRACK] = "${index + 1}/${targets.size}"
                try {
                    repo.saveTags(item, patch, cover)
                    succeeded++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures.add("${item.name}: ${friendly(e)}")
                }
            }
            clearBusy()
            _state.update { st ->
                st.copy(
                    screen = Screen.HOME,
                    revision = st.revision + 1,
                    report = if (failures.isEmpty()) null else Report(
                        title = "Batch edit finished",
                        summary = "$succeeded of ${targets.size} files updated, ${failures.size} failed.",
                        details = failures
                    )
                )
            }
            if (failures.isEmpty()) toast("$succeeded files updated")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Tag -> file name
    // ---------------------------------------------------------------------------------------

    fun openRename() {
        val s = _state.value
        val targets = s.items.filter { it.uri in s.selected }
        if (targets.isEmpty()) {
            toast("Select one or more files first")
            return
        }
        startRename(targets, Screen.HOME)
    }

    fun openRenameForEditor() {
        val editor = _state.value.editor ?: return
        if (editor.dirty) {
            toast("Save your tag changes first")
            return
        }
        startRename(listOf(editor.item), Screen.EDITOR)
    }

    private fun startRename(targets: List<AudioItem>, returnTo: Screen) {
        _state.update {
            it.copy(
                screen = Screen.RENAME,
                rename = it.rename.copy(
                    targets = targets,
                    returnTo = returnTo,
                    loading = true,
                    tags = emptyMap(),
                    rows = emptyList()
                )
            )
        }
        viewModelScope.launch {
            val tags = LinkedHashMap<Uri, TagData>()
            targets.forEachIndexed { index, item ->
                if (targets.size > 5) setBusy("Reading tags", index, targets.size)
                val read = runCatching { repo.quickTags(item) }.getOrNull()
                if (read != null) tags[item.uri] = read
            }
            clearBusy()
            _state.update { s ->
                s.copy(
                    rename = s.rename.copy(
                        loading = false,
                        tags = tags,
                        rows = computeRows(s.rename.targets, tags, s.rename.template)
                    )
                )
            }
        }
    }

    fun setTemplate(template: String) {
        _state.update { s ->
            s.copy(
                rename = s.rename.copy(
                    template = template,
                    rows = computeRows(s.rename.targets, s.rename.tags, template)
                )
            )
        }
    }

    private fun computeRows(
        targets: List<AudioItem>,
        tags: Map<Uri, TagData>,
        template: String
    ): List<RenameRow> {
        val used = HashSet<String>()
        return targets.map { item ->
            val data = tags[item.uri]
            if (data == null) {
                RenameRow(item, null, "Tags could not be read")
            } else {
                val base = FileNameTemplate.render(template, data, item.baseName)
                if (base.isBlank()) {
                    RenameRow(item, null, "Template gives an empty name")
                } else {
                    val suffix = if (item.extension.isEmpty()) "" else "." + item.extension
                    var candidate = base + suffix
                    var counter = 2
                    while (!used.add(candidate.lowercase())) {
                        candidate = "$base ($counter)$suffix"
                        counter++
                    }
                    RenameRow(item, candidate, if (candidate == item.name) "Already named like this" else null)
                }
            }
        }
    }

    fun applyRename() {
        val s = _state.value
        val rows = s.rename.rows.filter { it.newName != null && it.newName != it.item.name }
        if (rows.isEmpty()) {
            toast("Nothing to rename")
            return
        }
        prefs.edit().putString(PREF_TEMPLATE, s.rename.template).apply()
        viewModelScope.launch {
            val failures = ArrayList<String>()
            val renamed = HashMap<Uri, AudioItem>()
            rows.forEachIndexed { index, row ->
                val newName = row.newName ?: return@forEachIndexed
                setBusy("Renaming ${row.item.name}", index, rows.size)
                try {
                    val result = repo.rename(row.item, newName)
                    renamed[row.item.uri] = row.item.copy(uri = result.uri, name = result.name)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures.add("${row.item.name}: ${friendly(e)}")
                }
            }
            clearBusy()
            _state.update { st ->
                val editor = st.editor
                val updatedEditor = if (editor == null) {
                    null
                } else {
                    val moved = renamed[editor.item.uri]
                    if (moved != null) editor.copy(item = moved) else editor
                }
                st.copy(
                    screen = st.rename.returnTo,
                    items = st.items.map { renamed[it.uri] ?: it },
                    selected = st.selected.map { renamed[it]?.uri ?: it }.toSet(),
                    editor = updatedEditor,
                    revision = st.revision + 1,
                    report = if (failures.isEmpty()) null else Report(
                        title = "Rename finished",
                        summary = "${renamed.size} of ${rows.size} files renamed, ${failures.size} failed.",
                        details = failures
                    )
                )
            }
            if (failures.isEmpty()) toast("${renamed.size} files renamed")
        }
    }

    // ---------------------------------------------------------------------------------------
    // Navigation & dialogs
    // ---------------------------------------------------------------------------------------

    fun back() {
        val s = _state.value
        if (s.busy != null) return
        when (s.screen) {
            Screen.HOME -> Unit
            Screen.EDITOR -> {
                if (s.editor?.dirty == true) {
                    _state.update { it.copy(confirmDiscard = true) }
                } else {
                    closeEditor()
                }
            }
            Screen.BATCH -> _state.update { it.copy(screen = Screen.HOME) }
            Screen.RENAME -> _state.update { it.copy(screen = it.rename.returnTo) }
        }
    }

    fun discardAndClose() {
        closeEditor()
    }

    fun dismissDiscard() {
        _state.update { it.copy(confirmDiscard = false) }
    }

    fun dismissReport() {
        _state.update { it.copy(report = null) }
    }

    private fun closeEditor() {
        _state.update { it.copy(screen = Screen.HOME, editor = null, confirmDiscard = false) }
    }

    private companion object {
        const val PREF_TEMPLATE = "rename_template"
        const val PREF_COVER_PX = "cover_max_px"
        const val PREF_KEEP_ORIGINAL = "cover_keep_original"
    }
}
