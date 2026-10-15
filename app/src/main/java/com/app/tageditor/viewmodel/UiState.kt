package com.app.tageditor.viewmodel

import android.net.Uri
import com.app.tageditor.data.AudioInfo
import com.app.tageditor.data.AudioItem
import com.app.tageditor.data.CoverArt
import com.app.tageditor.data.CoverChange
import com.app.tageditor.data.FileNameTemplate
import com.app.tageditor.data.TagData
import com.app.tageditor.data.TagKey
import com.app.tageditor.data.diffTags

enum class Screen { HOME, EDITOR, BATCH, RENAME }

/** Shown as a blocking overlay while files are being read or written. */
data class BusyState(val message: String, val done: Int = 0, val total: Int = 0) {
    val progress: Float?
        get() = if (total > 0) done.toFloat() / total.toFloat() else null
}

/** State of the single-file editor. */
data class EditorState(
    val item: AudioItem,
    val loading: Boolean = true,
    val original: TagData = TagData(),
    val edited: TagData = TagData(),
    val info: AudioInfo = AudioInfo(),
    val embeddedCover: CoverArt? = null,
    val coverChange: CoverChange = CoverChange.Keep,
    val loadError: String? = null
) {
    val coverChanged: Boolean
        get() = coverChange !is CoverChange.Keep

    val dirty: Boolean
        get() = !loading && loadError == null && (diffTags(original, edited).isNotEmpty() || coverChanged)

    /** The cover as it will look after saving. */
    val shownCover: CoverArt?
        get() = when (val change = coverChange) {
            is CoverChange.Replace -> change.art
            CoverChange.Remove -> null
            CoverChange.Keep -> embeddedCover
        }
}

/** One field of the batch editor: only enabled fields are written. */
data class FieldDraft(val enabled: Boolean = false, val value: String = "")

enum class CoverMode { KEEP, REPLACE, REMOVE }

data class BatchDraft(
    val fields: Map<TagKey, FieldDraft> = emptyMap(),
    val numberTracks: Boolean = false,
    val coverMode: CoverMode = CoverMode.KEEP,
    val newCover: CoverArt? = null
) {
    fun field(key: TagKey): FieldDraft = fields[key] ?: FieldDraft()

    val hasChanges: Boolean
        get() = fields.values.any { it.enabled } ||
            numberTracks ||
            coverMode == CoverMode.REMOVE ||
            (coverMode == CoverMode.REPLACE && newCover != null)
}

/** One line of the rename preview. [newName] is null when the file cannot be renamed. */
data class RenameRow(val item: AudioItem, val newName: String?, val note: String? = null)

data class RenameDraft(
    val template: String = FileNameTemplate.DEFAULT,
    val targets: List<AudioItem> = emptyList(),
    val returnTo: Screen = Screen.HOME,
    val loading: Boolean = false,
    val tags: Map<Uri, TagData> = emptyMap(),
    val rows: List<RenameRow> = emptyList()
)

/** Summary dialog shown after batch operations that had problems. */
data class Report(val title: String, val summary: String, val details: List<String>)

data class UiState(
    val screen: Screen = Screen.HOME,
    val items: List<AudioItem> = emptyList(),
    val selected: Set<Uri> = emptySet(),
    /** Incremented whenever file contents changed so lists reload their tag summaries. */
    val revision: Int = 0,
    val busy: BusyState? = null,
    val editor: EditorState? = null,
    val batch: BatchDraft = BatchDraft(),
    val rename: RenameDraft = RenameDraft(),
    val coverMaxPx: Int = 1000,
    val keepOriginalCover: Boolean = false,
    val confirmDiscard: Boolean = false,
    val report: Report? = null
)
