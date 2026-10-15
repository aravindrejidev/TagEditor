package com.app.tageditor.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

private const val PERMISSION_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

/**
 * ACTION_OPEN_DOCUMENT with multi-select. Unlike the stock contract it also asks for *write*
 * access, which is what makes saving tags back into the original file possible.
 */
class OpenAudioDocuments : ActivityResultContract<Unit, List<Uri>>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("audio/*", "application/octet-stream", "video/mp4")
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(PERMISSION_FLAGS)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
        val uris = LinkedHashSet<Uri>()
        val clip = intent.clipData
        if (clip != null) {
            for (index in 0 until clip.itemCount) {
                val uri = clip.getItemAt(index).uri
                if (uri != null) uris.add(uri)
            }
        }
        val single = intent.data
        if (single != null) uris.add(single)
        return uris.toList()
    }
}

/** ACTION_OPEN_DOCUMENT_TREE with read + write grants for a whole album folder. */
class OpenFolder : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(PERMISSION_FLAGS)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

/** ACTION_CREATE_DOCUMENT: input is (suggested file name, mime type). */
class CreateFile : ActivityResultContract<Pair<String, String>, Uri?>() {

    override fun createIntent(context: Context, input: Pair<String, String>): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.second
            putExtra(Intent.EXTRA_TITLE, input.first)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}
