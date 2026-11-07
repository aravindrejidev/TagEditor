package com.app.tageditor.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Everything that touches storage.
 *
 * Songs come from the media library and the built-in file browser as plain `file://` paths (the
 * app has "All files access"); Storage Access Framework documents are still understood too.
 * Because jaudiotagger needs a real [File], each song is copied into a private cache folder,
 * edited there, verified, and only then written back to the original. The untouched original is
 * kept until the write-back succeeded and is restored automatically if anything goes wrong in
 * between.
 */
class TagEditorRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val workDir: File = File(appContext.cacheDir, "tag-work")

    /** jaudiotagger keeps global options, so file work is strictly one-at-a-time. */
    private val ioLock = Mutex()
    private val retrieverGate = Semaphore(3)
    private val quickCache = ConcurrentHashMap<String, TagData>()

    /** Small cover thumbnails for the song lists, and the songs known to have no cover. */
    private val thumbCache = object : LruCache<String, Bitmap>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val noCover = ConcurrentHashMap.newKeySet<String>()

    init {
        workDir.mkdirs()
        workDir.listFiles()?.forEach { it.delete() }
    }

    // ---------------------------------------------------------------------------------------
    // Picking & scanning
    // ---------------------------------------------------------------------------------------

    /** Every song of the media library (MP3, FLAC, M4A), sorted by file name. */
    @Suppress("DEPRECATION")
    suspend fun listLibrary(): List<AudioItem> = withContext(Dispatchers.IO) {
        val found = ArrayList<AudioItem>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.SIZE)
        val cursor = resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            null
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(0) ?: continue
                val name = path.substringAfterLast('/')
                if (!AudioFileParser.isSupported(name)) continue
                found.add(
                    AudioItem(
                        uri = Uri.fromFile(File(path)),
                        name = name,
                        sizeBytes = if (c.isNull(1)) 0L else c.getLong(1),
                        folder = LocalFiles.relativeFolder(path)
                    )
                )
            }
        }
        found.sortedWith(Comparator<AudioItem> { x, y -> NaturalOrder.compare(x.name, y.name) })
    }

    suspend fun storageRoots(): List<StorageRoot> = withContext(Dispatchers.IO) {
        LocalFiles.roots(appContext)
    }

    suspend fun listDirectory(dir: File): DirListing = withContext(Dispatchers.IO) {
        LocalFiles.list(dir)
    }

    /**
     * A small cover picture for song lists, read from the file's embedded art. Results are cached;
     * null means the song has no (readable) embedded cover.
     */
    suspend fun coverThumb(item: AudioItem): Bitmap? {
        val key = item.uri.toString()
        thumbCache.get(key)?.let { return it }
        if (key in noCover) return null
        val bitmap = retrieverGate.withPermit {
            withContext(Dispatchers.IO) { readThumb(item.uri) }
        }
        if (bitmap != null) thumbCache.put(key, bitmap) else noCover.add(key)
        return bitmap
    }

    private fun readThumb(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val decoded = ImageTools.decodeSampled(bytes, THUMB_PX) ?: return null
            val longest = max(decoded.width, decoded.height)
            if (longest <= THUMB_PX) {
                decoded
            } else {
                val scale = THUMB_PX.toFloat() / longest
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * scale).roundToInt().coerceAtLeast(1),
                    (decoded.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /** Drops everything cached for a file whose content or name just changed. */
    private fun forget(uri: Uri) {
        val key = uri.toString()
        quickCache.remove(key)
        thumbCache.remove(key)
        noCover.remove(key)
    }

    /** Tells the media library about changed or renamed files so other apps see the new tags. */
    private fun rescan(vararg uris: Uri) {
        val paths = uris.filter { it.scheme == "file" }.mapNotNull { it.path }
        if (paths.isEmpty()) return
        try {
            MediaScannerConnection.scanFile(appContext, paths.toTypedArray(), null, null)
        } catch (e: Exception) {
            // Not important: the library catches up on its own later.
        }
    }

    /** Resolves names and sizes for documents returned by ACTION_OPEN_DOCUMENT. */
    suspend fun describe(uris: List<Uri>): List<AudioItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<AudioItem>()
        for (uri in uris) {
            persistPermission(uri)
            val info = queryNameAndSize(uri)
            val name = info?.first ?: uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
            result.add(AudioItem(uri = uri, name = name, sizeBytes = info?.second ?: 0L))
        }
        result
    }

    /** Walks a folder picked with ACTION_OPEN_DOCUMENT_TREE and returns every supported file. */
    suspend fun scanTree(treeUri: Uri, recursive: Boolean = true): List<AudioItem> =
        withContext(Dispatchers.IO) {
            persistPermission(treeUri)
            val found = ArrayList<AudioItem>()
            val pending = ArrayList<Pair<String, String>>()
            pending.add(Pair(DocumentsContract.getTreeDocumentId(treeUri), ""))
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            while (pending.isNotEmpty() && found.size < MAX_SCAN_RESULTS) {
                val (parentId, path) = pending.removeAt(pending.lastIndex)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                val cursor = try {
                    resolver.query(childrenUri, projection, null, null, null)
                } catch (e: Exception) {
                    null
                }
                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val docId = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2).orEmpty()
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (recursive) {
                                pending.add(Pair(docId, if (path.isEmpty()) name else "$path/$name"))
                            }
                        } else if (AudioFileParser.isSupported(name)) {
                            val size = if (c.isNull(3)) 0L else c.getLong(3)
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            found.add(AudioItem(docUri, name, size, path))
                        }
                    }
                }
            }
            found.sortedWith(Comparator<AudioItem> { x, y ->
                val byFolder = NaturalOrder.compare(x.folder, y.folder)
                if (byFolder != 0) byFolder else NaturalOrder.compare(x.name, y.name)
            })
        }

    private fun persistPermission(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Not every provider supports persistable grants; the temporary grant is enough.
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long>? {
        return try {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    if (name == null) {
                        null
                    } else {
                        Pair(name, if (c.isNull(1)) 0L else c.getLong(1))
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    /** Full read with jaudiotagger (tags, technical info, embedded cover). */
    suspend fun load(item: AudioItem): ParsedAudio = ioLock.withLock {
        withContext(Dispatchers.IO) {
            val temp = newTempFile(item, "read")
            try {
                copyUriToFile(item.uri, temp)
                AudioFileParser.read(temp)
            } finally {
                temp.delete()
            }
        }
    }

    /**
     * Fast tag lookup for lists and file renaming. Uses the platform's MediaMetadataRetriever
     * (no copy of the file needed) and falls back to a full jaudiotagger read.
     */
    suspend fun quickTags(item: AudioItem): TagData {
        val key = item.uri.toString()
        quickCache[key]?.let { return it }
        val viaRetriever = retrieverGate.withPermit {
            withContext(Dispatchers.IO) { readWithRetriever(item.uri) }
        }
        val tags = if (viaRetriever != null && !viaRetriever.isEmpty()) {
            viaRetriever
        } else {
            try {
                load(item).tags
            } catch (e: Exception) {
                TagData()
            }
        }
        quickCache[key] = tags
        return tags
    }

    private fun readWithRetriever(uri: Uri): TagData? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            fun meta(code: Int): String = retriever.extractMetadata(code)?.trim().orEmpty()
            val year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR).ifBlank {
                meta(MediaMetadataRetriever.METADATA_KEY_DATE)
            }
            TagData(
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                year = year,
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                track = meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                disc = meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                composer = meta(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------

    /**
     * Writes [patch] and [cover] into the document. Safe by construction: the edit happens on a
     * private copy, is re-read to verify the audio stream is untouched, and the original is
     * restored if copying back fails.
     */
    suspend fun saveTags(item: AudioItem, patch: Map<TagKey, String>, cover: CoverChange) {
        ioLock.withLock {
            withContext(Dispatchers.IO) {
                saveBlocking(item, patch, cover)
            }
        }
        forget(item.uri)
        rescan(item.uri)
    }

    private fun saveBlocking(item: AudioItem, patch: Map<TagKey, String>, cover: CoverChange) {
        val backup = newTempFile(item, "orig")
        val work = newTempFile(item, "work")
        var writeStarted = false
        try {
            copyUriToFile(item.uri, backup)
            backup.copyTo(work, overwrite = true)

            val before = AudioFileParser.read(work)
            AudioFileParser.write(work, patch, cover, workDir)
            val after = AudioFileParser.read(work)
            verifyAudioUntouched(before.info, after.info)

            writeStarted = true
            copyFileToUri(work, item.uri)
        } catch (e: Throwable) {
            if (writeStarted) {
                // The document may be half-written: put the original bytes back.
                try {
                    copyFileToUri(backup, item.uri)
                } catch (restoreError: Throwable) {
                    // Nothing more we can do.
                }
            }
            throw e
        } finally {
            backup.delete()
            work.delete()
        }
    }

    private fun verifyAudioUntouched(before: AudioInfo, after: AudioInfo) {
        val sameStream = before.sampleRateHz == after.sampleRateHz &&
            before.channels == after.channels &&
            abs(before.durationSeconds - after.durationSeconds) <= 1
        if (!sameStream) {
            throw IOException("Safety check failed: the audio stream changed. The original file was not modified.")
        }
    }

    /** Renames the file in place (the audio and tags are not touched). */
    suspend fun rename(item: AudioItem, newName: String): RenamedDoc = withContext(Dispatchers.IO) {
        if (item.uri.scheme == "file") {
            return@withContext renameFile(item, newName)
        }
        val newUri = DocumentsContract.renameDocument(resolver, item.uri, newName)
            ?: throw IOException("The storage provider refused to rename this file")
        forget(item.uri)
        val actualName = queryNameAndSize(newUri)?.first ?: newName
        RenamedDoc(newUri, actualName)
    }

    private fun renameFile(item: AudioItem, newName: String): RenamedDoc {
        val source = File(item.uri.path ?: throw IOException("Unknown file location"))
        val parent = source.parentFile ?: throw IOException("Unknown folder")
        val target = File(parent, newName)
        // A change of letter case only is the same file on case-insensitive storage.
        if (target.exists() && !source.name.equals(newName, ignoreCase = true)) {
            throw IOException("A file named \"$newName\" already exists in this folder")
        }
        if (!source.renameTo(target)) throw IOException("Android refused to rename this file")
        forget(item.uri)
        val newUri = Uri.fromFile(target)
        rescan(item.uri, newUri)
        return RenamedDoc(newUri, target.name)
    }

    /** Reads an image picked from the gallery and prepares it for embedding. */
    suspend fun prepareCover(uri: Uri, maxPx: Int, keepOriginal: Boolean): CoverArt =
        withContext(Dispatchers.IO) {
            val raw = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Could not read the selected image")
            ImageTools.prepareCover(raw, maxPx, keepOriginal)
        }

    /**
     * Reads a lyrics file (LRC, ELRC, TTML, TXT) picked by the user. The text is returned
     * unchanged apart from line breaks, so timestamps survive into the tag.
     */
    suspend fun readLyricsFile(uri: Uri): LyricsFile = withContext(Dispatchers.IO) {
        val name = queryNameAndSize(uri)?.first
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "lyrics"
        if (!LyricsFiles.isSupported(name)) {
            throw IOException("Unsupported file. Choose an .lrc, .elrc, .ttml, .xml or .txt file")
        }
        val bytes = resolver.openInputStream(uri)?.use { readLimited(it, LyricsFiles.MAX_BYTES) }
            ?: throw IOException("Could not open the lyrics file")
        LyricsFiles.parse(name, bytes)
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw IOException("The lyrics file is larger than ${limit / (1024 * 1024)} MB")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Saves arbitrary bytes (an extracted cover image) to a document created by the user. */
    suspend fun writeBytes(target: Uri, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            openForOverwrite(target).use { out ->
                out.write(bytes)
                out.flush()
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Low level copy helpers
    // ---------------------------------------------------------------------------------------

    private fun newTempFile(item: AudioItem, role: String): File {
        val ext = item.extension.ifEmpty { "bin" }
        workDir.mkdirs()
        return File.createTempFile(role + "_", ".$ext", workDir)
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the file")
        input.use { source ->
            FileOutputStream(target).use { sink ->
                source.copyTo(sink, COPY_BUFFER)
            }
        }
    }

    private fun copyFileToUri(source: File, uri: Uri) {
        openForOverwrite(uri).use { sink ->
            FileInputStream(source).use { input ->
                input.copyTo(sink, COPY_BUFFER)
            }
            sink.flush()
        }
    }

    /** Opens a document for writing and truncates it ("wt"; plain "w" does not truncate on Android 10). */
    private fun openForOverwrite(uri: Uri): OutputStream {
        return try {
            resolver.openOutputStream(uri, "wt") ?: throw IOException("Could not open the file for writing")
        } catch (first: Exception) {
            val pfd = try {
                resolver.openFileDescriptor(uri, "rwt")
            } catch (e: Exception) {
                null
            } ?: throw first
            ParcelFileDescriptor.AutoCloseOutputStream(pfd)
        }
    }

    private companion object {
        const val COPY_BUFFER = 256 * 1024
        const val MAX_SCAN_RESULTS = 5000
        const val THUMB_PX = 160
        const val THUMB_CACHE_BYTES = 24 * 1024 * 1024
    }
}
