package com.app.tageditor.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

/** A storage volume the browser can start from: internal storage, an SD card or a USB drive. */
data class StorageRoot(val label: String, val path: String)

data class FolderEntry(val name: String, val path: String)

/** Sub-folders and supported songs of one folder, both sorted naturally. */
data class DirListing(val folders: List<FolderEntry>, val files: List<AudioItem>)

/**
 * What Tag Editor needs to list songs and write tags straight into files:
 * the audio permission (so the media library can be read) and, from Android 11, the
 * "All files access" switch. Android 10 and older use the classic storage permissions.
 */
object StorageAccess {

    fun runtimePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else if (Build.VERSION.SDK_INT >= 30) {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    fun hasRuntimePermissions(context: Context): Boolean {
        return runtimePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
    }

    fun hasAccess(context: Context): Boolean {
        return hasRuntimePermissions(context) && hasAllFilesAccess()
    }

    /** Opens the "All files access" switch of this app (Android 11+). */
    fun allFilesIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
    }

    /** The generic list of apps with that switch, for phones that cannot open the page above. */
    fun allFilesListIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

    fun appSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:" + context.packageName)
        )
    }
}

/** Plain `java.io.File` helpers for the library and the built-in file browser. */
object LocalFiles {

    /** Internal storage first, then every mounted SD card or USB drive the system reports. */
    fun roots(context: Context): List<StorageRoot> {
        val result = ArrayList<StorageRoot>()
        val internal = Environment.getExternalStorageDirectory().absolutePath
        result.add(StorageRoot("Internal storage", internal))
        try {
            val manager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            for (volume in manager.storageVolumes) {
                if (volume.isPrimary) continue
                if (volume.state != Environment.MEDIA_MOUNTED) continue
                val dir = volumeDirectory(volume) ?: continue
                val path = dir.absolutePath
                if (result.none { it.path == path }) {
                    result.add(StorageRoot(volume.getDescription(context) ?: "SD card", path))
                }
            }
        } catch (e: Exception) {
            // Internal storage alone is still fully usable.
        }
        return result
    }

    private fun volumeDirectory(volume: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= 30) return volume.directory
        return try {
            val path = StorageVolume::class.java.getMethod("getPath").invoke(volume) as? String
            if (path.isNullOrEmpty()) null else File(path)
        } catch (e: Exception) {
            null
        }
    }

    /** Lists one folder: sub-folders and supported audio files. Hidden entries are skipped. */
    fun list(dir: File): DirListing {
        val children = dir.listFiles() ?: throw IOException("This folder cannot be read")
        val folders = ArrayList<FolderEntry>()
        val files = ArrayList<AudioItem>()
        for (child in children) {
            val name = child.name
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                folders.add(FolderEntry(name, child.absolutePath))
            } else if (AudioFileParser.isSupported(name)) {
                files.add(
                    AudioItem(
                        uri = Uri.fromFile(child),
                        name = name,
                        sizeBytes = child.length()
                    )
                )
            }
        }
        folders.sortWith(Comparator<FolderEntry> { a, b -> NaturalOrder.compare(a.name, b.name) })
        files.sortWith(Comparator<AudioItem> { a, b -> NaturalOrder.compare(a.name, b.name) })
        return DirListing(folders, files)
    }

    /** "Music/Album" for `/storage/emulated/0/Music/Album/song.mp3`, shown under library songs. */
    fun relativeFolder(path: String): String {
        val parent = File(path).parent ?: return ""
        val internal = Environment.getExternalStorageDirectory().absolutePath
        return when {
            parent == internal -> ""
            parent.startsWith("$internal/") -> parent.substring(internal.length + 1)
            parent.startsWith("/storage/") -> parent.removePrefix("/storage/")
            else -> parent
        }
    }
}
