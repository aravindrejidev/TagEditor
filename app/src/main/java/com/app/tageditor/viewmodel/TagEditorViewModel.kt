package com.app.tageditor.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.tageditor.data.AudioItem
import com.app.tageditor.data.CoverArt
import com.app.tageditor.data.CoverChange
import com.app.tageditor.data.FileNameTemplate
import com.app.tageditor.data.StorageAccess
import com.app.tageditor.data.TagData
import com.app.tageditor.data.TagEditorRepository
import com.app.tageditor.data.TagKey
import com.app.tageditor.data.diffTags
import com.app.tageditor.data.friendlyPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException

class TagEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TagEditorRepository(application)
    private val prefs = application.getSharedPreferences("tag_editor_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            hasAccess = StorageAccess.hasAccess(application),
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

    init {
        if (_state.value.hasAccess) {
            loadLibrary()
            startBrowser()
        }
    }

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
            "No write permission for this file. Check that Tag Editor still has \"All files access\" in the phone's settings."
        is FileNotFoundException -> "The file was not found or cannot be written."
        is OutOfMemoryError -> "Not enough memory to process this file."
        else -> t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
    }

    /** Suspend-safe item lookup used by the list rows. */
    suspend fun tagsFor(item: AudioItem): TagData = repo.quickTags(item)

    /** Small cover picture for a list row, or null when the song has no embedded cover. */
    suspend fun coverFor(item: AudioItem): Bitmap? = repo.coverThumb(item)

    fun showMessage(text: String) {
        toast(text)
    }

    // ---------------------------------------------------------------------------------------
    // Finding songs: access, the "All" tab and the built-in file browser
    // ---------------------------------------------------------------------------------------

    /** Called when the app returns to the foreground: the user may have changed a permission. */
    fun refreshAccess() {
        val granted = StorageAccess.hasAccess(getApplication<Application>())
        if (granted == _state.value.hasAccess) return
        _state.update { it.copy(hasAccess = granted, selected = emptySet()) }
        if (granted) {
            loadLibrary()
            startBrowser()
        }
    }

    fun selectTab(tab: HomeTab) {
        _state.update { s -> if (s.tab == tab) s else s.copy(tab = tab, selected = emptySet()) }
    }

    /** Reads every song of the media library. Also used by the rescan button. */
    fun loadLibrary() {
        if (!_state.value.hasAccess) return
        viewModelScope.launch {
            _state.update { it.copy(libraryLoading = true) }
            val result = runCatching { repo.listLibrary() }
            val list = result.getOrNull()
            _state.update { s ->
                if (list == null) {
                    s.copy(libraryLoading = false)
                } else {
                    val known = list.map { it.uri }.toHashSet()
                    s.copy(
                        library = list,
                        libraryLoading = false,
                        selected = if (s.tab == HomeTab.ALL) s.selected.filter { it in known }.toSet() else s.selected
                    )
                }
            }
            val error = result.exceptionOrNull()
            if (error != null) toast("Could not read your music library: ${friendly(error)}")
        }
    }

    private fun startBrowser() {
        viewModelScope.launch {
            val roots = runCatching { repo.storageRoots() }.getOrDefault(emptyList())
            val saved = prefs.getString(PREF_BROWSE_DIR, null)
            val usable = saved != null &&
                roots.any { saved == it.path || saved.startsWith(it.path + "/") } &&
                File(saved).isDirectory
            val start = if (usable && saved != null) saved else roots.firstOrNull()?.path
            _state.update { it.copy(browser = it.browser.copy(roots = roots)) }
            if (start != null) browseTo(start)
        }
    }

    /** Opens a folder in the built-in browser. Songs ticked in another folder are unticked. */
    fun browseTo(path: String) {
        _state.update { s ->
            s.copy(
                browser = s.browser.copy(
                    dir = path,
                    folders = emptyList(),
                    files = emptyList(),
                    loading = true,
                    error = null
                ),
                selected = emptySet()
            )
        }
        viewModelScope.launch {
            val result = runCatching { repo.listDirectory(File(path)) }
            val listing = result.getOrNull()
            _state.update { s ->
                if (s.browser.dir != path) {
                    // The user already moved on to another folder.
                    s
                } else if (listing != null) {
                    s.copy(
                        browser = s.browser.copy(
                            folders = listing.folders,
                            files = listing.files,
                            loading = false,
                            error = null
                        )
                    )
                } else {
                    val reason = result.exceptionOrNull()?.let { friendly(it) } ?: "This folder cannot be read"
                    s.copy(browser = s.browser.copy(loading = false, error = reason))
                }
            }
            if (listing != null) prefs.edit().putString(PREF_BROWSE_DIR, path).apply()
        }
    }

    fun browseUp() {
        val browser = _state.value.browser
        if (!browser.canGoUp) return
        val parent = File(browser.dir).parent ?: return
        browseTo(parent)
    }

    fun toggleSelected(uri: Uri) {
        _state.update { s ->
            s.copy(selected = if (uri in s.selected) s.selected - uri else s.selected + uri)
        }
    }

    /** Selects exactly these songs (the visible ones, when a search is active). */
    fun selectItems(items: List<AudioItem>) {
        _state.update { s -> s.copy(selected = items.map { it.uri }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    // ---------------------------------------------------------------------------------------
    // Single file editor
    // ---------------------------------------------------------------------------------------

    fun openEditor(item: AudioItem) {
        _state.update {
            it.copy(
                screen = Screen.EDITOR,
                editor = EditorState(item = item, path = friendlyPath(item.uri, item.name))
            )
        }
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

    /**
     * Fills the lyrics field from an LRC / ELRC / TTML / TXT file. The text goes into the editor
     * like a typed edit, so it is only written to the song when the user taps Save.
     */
    fun importLyricsFile(uri: Uri) {
        val target = _state.value.editor?.item?.uri ?: return
        viewModelScope.launch {
            setBusy("Reading lyrics file")
            val result = runCatching { repo.readLyricsFile(uri) }
            clearBusy()
            result.onSuccess { file ->
                _state.update { s ->
                    val current = s.editor
                    if (current == null || current.item.uri != target) {
                        s
                    } else {
                        s.copy(
                            editor = current.copy(
                                edited = current.edited.withValue(TagKey.LYRICS, file.text)
                            )
                        )
                    }
                }
                toast("Lyrics loaded from ${file.name} (${file.format.label}). Tap Save to write them.")
            }.onFailure { toast("Could not import lyrics: ${friendly(it)}") }
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

    fun setRenameAsTitle(enabled: Boolean) {
        _state.update { s ->
            val current = s.editor
            if (current == null) s else s.copy(editor = current.copy(renameAsTitle = enabled))
        }
    }

    private fun titleFileName(tags: TagData, item: AudioItem): String? {
        val base = FileNameTemplate.render("%title%", tags, item.baseName)
        if (base.isBlank()) return null
        val suffix = if (item.extension.isEmpty()) "" else "." + item.extension
        return base + suffix
    }

    fun saveEditor() {
        val editor = _state.value.editor ?: return
        if (!editor.canSave) return
        val patch = diffTags(editor.original, editor.edited)
        val tagsChanged = patch.isNotEmpty() || editor.coverChanged
        viewModelScope.launch {
            setBusy("Saving")
            var item = editor.item
            var saveError: Throwable? = null
            var renameMessage: String? = null

            if (tagsChanged) {
                saveError = runCatching { repo.saveTags(item, patch, editor.coverChange) }.exceptionOrNull()
            }
            if (saveError == null && editor.renameAsTitle) {
                val newName = titleFileName(editor.edited, item)
                if (newName != null && newName != item.name) {
                    val result = runCatching { repo.rename(item, newName) }
                    val doc = result.getOrNull()
                    if (doc != null) {
                        item = item.copy(uri = doc.uri, name = doc.name)
                    } else {
                        val error = result.exceptionOrNull()
                        renameMessage = if (error != null) friendly(error) else "unknown error"
                    }
                }
            }

            if (saveError == null) {
                val reloaded = runCatching { repo.load(item) }.getOrNull()
                val oldUri = editor.item.uri
                val moved = item
                _state.update { s ->
                    val mapped = s.mapItems { if (it.uri == oldUri) moved else it }
                    val selected = s.selected.map { if (it == oldUri) moved.uri else it }.toSet()
                    val current = s.editor
                    if (current == null || current.item.uri != oldUri) {
                        mapped.copy(selected = selected, revision = s.revision + 1)
                    } else {
                        val refreshed = if (reloaded != null) {
                            current.copy(
                                item = moved,
                                path = friendlyPath(moved.uri, moved.name),
                                original = reloaded.tags,
                                edited = reloaded.tags,
                                info = reloaded.info,
                                embeddedCover = reloaded.cover,
                                coverChange = CoverChange.Keep,
                                renameAsTitle = false
                            )
                        } else {
                            current.copy(
                                item = moved,
                                path = friendlyPath(moved.uri, moved.name),
                                original = current.edited,
                                embeddedCover = current.shownCover,
                                coverChange = CoverChange.Keep,
                                renameAsTitle = false
                            )
                        }
                        mapped.copy(editor = refreshed, selected = selected, revision = s.revision + 1)
                    }
                }
                if (renameMessage != null) {
                    toast("Tags saved, but the file could not be renamed: $renameMessage")
                } else {
                    toast("Saved")
                }
            } else {
                toast("Save failed: ${saveError?.let { friendly(it) } ?: "unknown error"}")
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
        val s = _state.value
        val targets = s.items.filter { it.uri in s.selected }
        if (targets.isEmpty()) {
            toast("Select one or more files first")
            return
        }
        if (targets.size == 1) {
            // One song: the full editor is the better tool.
            openEditor(targets.first())
            return
        }
        _state.update { it.copy(screen = Screen.BATCH, batch = BatchDraft(loading = true)) }
        viewModelScope.launch {
            val rows = ArrayList<Pair<AudioItem, TagData>>()
            targets.forEachIndexed { index, item ->
                if (targets.size > 5) setBusy("Reading tags", index, targets.size)
                val tags = runCatching { repo.quickTags(item) }.getOrNull()
                if (tags != null) rows.add(Pair(item, tags))
            }
            clearBusy()

            val prefilled = LinkedHashMap<TagKey, FieldDraft>()
            val variants = LinkedHashMap<TagKey, List<String>>()
            for (key in BATCH_KEYS) {
                val values = rows.map { it.second[key].trim() }
                val distinct = values.filter { it.isNotEmpty() }.distinct()
                if (rows.isNotEmpty() && distinct.size == 1 && values.none { it.isEmpty() }) {
                    prefilled[key] = FieldDraft(enabled = false, value = distinct[0])
                } else if (distinct.isNotEmpty()) {
                    variants[key] = distinct.take(3)
                }
            }
            _state.update { st ->
                if (st.screen != Screen.BATCH) {
                    st
                } else {
                    st.copy(
                        batch = st.batch.copy(
                            loading = false,
                            fields = prefilled + st.batch.fields,
                            variants = variants,
                            overview = rows
                        )
                    )
                }
            }
        }
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
                st.mapItems { renamed[it.uri] ?: it }.copy(
                    screen = st.rename.returnTo,
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
            Screen.HOME -> {
                // In the file browser the back button goes up one folder.
                if (s.tab == HomeTab.BROWSE && s.browser.canGoUp) browseUp()
            }
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
        val BATCH_KEYS = listOf(
            TagKey.ALBUM, TagKey.ALBUM_ARTIST, TagKey.ARTIST, TagKey.YEAR,
            TagKey.GENRE, TagKey.DISC, TagKey.COMPOSER, TagKey.COMMENT
        )
        const val PREF_BROWSE_DIR = "browse_dir"
        const val PREF_TEMPLATE = "rename_template"
        const val PREF_COVER_PX = "cover_max_px"
        const val PREF_KEEP_ORIGINAL = "cover_keep_original"
    }
}
