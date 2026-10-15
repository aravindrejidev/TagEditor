package com.app.tageditor.data

import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v22Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import java.io.File
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads and writes tags of MP3 (ID3v1/ID3v2), FLAC (Vorbis comments) and M4A/MP4 files with
 * jaudiotagger. It always works on a real [File] (the repository hands over a private copy of the
 * SAF document). Only tag blocks are rewritten, the audio stream itself is never re-encoded.
 */
object AudioFileParser {

    private const val FRONT_COVER = 3

    private val supportedExtensions = setOf("mp3", "flac", "m4a", "m4b", "mp4")

    @Volatile
    private var initialised = false

    fun isSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in supportedExtensions

    fun formatLabel(fileName: String): String {
        return when (val ext = fileName.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "MP3"
            "flac" -> "FLAC"
            "m4a", "m4b" -> "M4A"
            "mp4" -> "MP4"
            else -> ext.uppercase()
        }
    }

    // ---------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------

    @Synchronized
    private fun ensureInitialised() {
        if (initialised) return
        runCatching { Logger.getLogger("org.jaudiotagger").level = Level.OFF }
        val options = TagOptionSingleton.getInstance()
        // Android mode: no java.awt / ImageIO, no sun.* internals. Set by reflection so the build
        // does not depend on the exact option names of the library version.
        setBooleanOption(options, "setAndroid", true)
        // Plain (non-unsynchronised) tags are understood by more players.
        setBooleanOption(options, "setUnsyncTags", false)
        initialised = true
    }

    private fun setBooleanOption(target: Any, methodName: String, value: Boolean) {
        try {
            target.javaClass.getMethod(methodName, java.lang.Boolean.TYPE).invoke(target, value)
        } catch (e: Throwable) {
            // Option not available in this version of the library: ignore.
        }
    }

    // ---------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------

    fun read(file: File): ParsedAudio {
        ensureInitialised()
        val audioFile = AudioFileIO.read(file)
        val tag = readableTag(audioFile)
        return ParsedAudio(
            tags = if (tag != null) extractTags(tag) else TagData(),
            info = extractInfo(audioFile, file.name),
            cover = if (tag != null) extractCover(tag) else null
        )
    }

    private fun readableTag(audioFile: AudioFile): Tag? {
        if (audioFile is MP3File) {
            val v2: Tag? = audioFile.getID3v2Tag()
            if (v2 != null) return v2
            val v1: Tag? = audioFile.getID3v1Tag()
            return v1
        }
        val tag: Tag? = audioFile.getTag()
        return tag
    }

    private fun readField(tag: Tag, key: FieldKey): String {
        return try {
            tag.getFirst(key) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractTags(tag: Tag): TagData {
        return TagData(
            title = readField(tag, FieldKey.TITLE),
            artist = readField(tag, FieldKey.ARTIST),
            album = readField(tag, FieldKey.ALBUM),
            albumArtist = readField(tag, FieldKey.ALBUM_ARTIST),
            year = readField(tag, FieldKey.YEAR),
            genre = readField(tag, FieldKey.GENRE),
            track = joinNumber(readField(tag, FieldKey.TRACK), readField(tag, FieldKey.TRACK_TOTAL)),
            disc = joinNumber(readField(tag, FieldKey.DISC_NO), readField(tag, FieldKey.DISC_TOTAL)),
            composer = readField(tag, FieldKey.COMPOSER),
            comment = readField(tag, FieldKey.COMMENT),
            lyrics = readField(tag, FieldKey.LYRICS)
        )
    }

    private fun joinNumber(number: String, total: String): String {
        val n = number.trim()
        val t = total.trim()
        return when {
            n.isEmpty() -> ""
            t.isEmpty() || t == "0" -> n
            else -> "$n/$t"
        }
    }

    private fun extractInfo(audioFile: AudioFile, fileName: String): AudioInfo {
        val header = audioFile.getAudioHeader()
        val lossless = try {
            header.isLossless()
        } catch (e: Throwable) {
            false
        }
        val variable = try {
            header.isVariableBitRate()
        } catch (e: Throwable) {
            false
        }
        return AudioInfo(
            format = formatLabel(fileName),
            encoding = safeString { header.getEncodingType() },
            durationSeconds = safeInt { header.getTrackLength() },
            bitRateKbps = safeLong { header.getBitRateAsNumber() },
            sampleRateHz = safeInt { header.getSampleRateAsNumber() },
            channels = safeString { header.getChannels() },
            bitsPerSample = optionalInt(header, "getBitsPerSample"),
            lossless = lossless,
            variableBitRate = variable
        )
    }

    private inline fun safeString(block: () -> String?): String {
        return try {
            block()?.trim().orEmpty()
        } catch (e: Throwable) {
            ""
        }
    }

    private inline fun safeInt(block: () -> Int): Int {
        return try {
            block()
        } catch (e: Throwable) {
            0
        }
    }

    private inline fun safeLong(block: () -> Long): Long {
        return try {
            block()
        } catch (e: Throwable) {
            0L
        }
    }

    /** Calls an optional no-argument int getter by reflection (not present in every version). */
    private fun optionalInt(target: Any, methodName: String): Int {
        return try {
            (target.javaClass.getMethod(methodName).invoke(target) as? Number)?.toInt() ?: 0
        } catch (e: Throwable) {
            0
        }
    }

    private fun extractCover(tag: Tag): CoverArt? {
        return try {
            val artworks = tag.getArtworkList()
            val picked = artworks.firstOrNull { it.getPictureType() == FRONT_COVER }
                ?: artworks.firstOrNull()
            if (picked == null) {
                null
            } else {
                val data: ByteArray? = picked.getBinaryData()
                if (data == null || data.isEmpty()) {
                    null
                } else {
                    CoverArt(data, resolveMime(picked.getMimeType(), data))
                }
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun resolveMime(declared: String?, data: ByteArray): String {
        val sniffed = ImageTools.sniffMime(data)
        if (sniffed != null) return sniffed
        return declared?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    }

    // ---------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------

    private class TagTarget(val primary: Tag, val legacy: List<Tag>, val created: Boolean)

    /**
     * Applies [patch] (blank value = delete the field) and the [cover] change to [file] and
     * commits it. [scratchDir] is used for a short-lived temp file when embedding artwork.
     */
    fun write(file: File, patch: Map<TagKey, String>, cover: CoverChange, scratchDir: File) {
        ensureInitialised()
        val audioFile = AudioFileIO.read(file)
        val target = prepareTag(audioFile)

        if (target.created && target.legacy.isNotEmpty()) {
            // MP3 that only had an ID3v1 tag: carry its values over into the new ID3v2 tag.
            val seed = extractTags(target.legacy.first())
            val seedPatch = TagKey.values()
                .associateWith { seed[it] }
                .filterValues { it.isNotBlank() }
            applyPatch(target.primary, seedPatch, strict = false)
        }

        applyPatch(target.primary, patch, strict = true)
        for (legacyTag in target.legacy) {
            // Keep a pre-existing ID3v1 tag in sync (best effort, it only has a few fields).
            applyPatch(legacyTag, patch, strict = false)
        }

        when (cover) {
            CoverChange.Keep -> Unit
            CoverChange.Remove -> removeCover(target.primary)
            is CoverChange.Replace -> setCover(target.primary, cover.art, scratchDir)
        }

        audioFile.commit()
    }

    private fun prepareTag(audioFile: AudioFile): TagTarget {
        if (audioFile is MP3File) {
            val v1: Tag? = audioFile.getID3v1Tag()
            val legacy: List<Tag> = if (v1 != null) listOf(v1) else emptyList()
            var v2: Tag? = audioFile.getID3v2Tag()
            var created = false
            if (v2 == null) {
                val fresh = ID3v23Tag()
                audioFile.setTag(fresh)
                v2 = fresh
                created = true
            } else if (v2 is ID3v22Tag) {
                // ID3v2.2 is obsolete: convert it to the library's default modern version.
                v2 = audioFile.getTagAndConvertOrCreateAndSetDefault()
            }
            val primary: Tag = v2 ?: throw IOException("Could not create an ID3v2 tag")
            return TagTarget(primary, legacy, created)
        }
        val existing: Tag? = audioFile.getTag()
        if (existing != null) return TagTarget(existing, emptyList(), false)
        val fresh: Tag = audioFile.getTagOrCreateAndSetDefault()
        return TagTarget(fresh, emptyList(), true)
    }

    private fun applyPatch(tag: Tag, patch: Map<TagKey, String>, strict: Boolean) {
        for ((key, rawValue) in patch) {
            val value = rawValue.trim()
            try {
                when (key) {
                    TagKey.TRACK -> applyNumber(tag, FieldKey.TRACK, FieldKey.TRACK_TOTAL, value)
                    TagKey.DISC -> applyNumber(tag, FieldKey.DISC_NO, FieldKey.DISC_TOTAL, value)
                    else -> applyText(tag, fieldKeyOf(key), value)
                }
            } catch (e: Exception) {
                if (strict) throw e
            }
        }
    }

    private fun fieldKeyOf(key: TagKey): FieldKey = when (key) {
        TagKey.TITLE -> FieldKey.TITLE
        TagKey.ARTIST -> FieldKey.ARTIST
        TagKey.ALBUM -> FieldKey.ALBUM
        TagKey.ALBUM_ARTIST -> FieldKey.ALBUM_ARTIST
        TagKey.YEAR -> FieldKey.YEAR
        TagKey.GENRE -> FieldKey.GENRE
        TagKey.TRACK -> FieldKey.TRACK
        TagKey.DISC -> FieldKey.DISC_NO
        TagKey.COMPOSER -> FieldKey.COMPOSER
        TagKey.COMMENT -> FieldKey.COMMENT
        TagKey.LYRICS -> FieldKey.LYRICS
    }

    private fun applyText(tag: Tag, key: FieldKey, value: String) {
        if (value.isEmpty()) {
            deleteQuietly(tag, key)
        } else {
            tag.setField(key, value)
        }
    }

    /** Deleting a field that does not exist is not an error. */
    private fun deleteQuietly(tag: Tag, key: FieldKey) {
        try {
            tag.deleteField(key)
        } catch (e: Exception) {
            // Field not present (or not supported by this tag type).
        }
    }

    /** "3" sets only the number, "3/12" sets number and total, blank removes both. */
    private fun applyNumber(tag: Tag, numberKey: FieldKey, totalKey: FieldKey, value: String) {
        if (value.isEmpty()) {
            deleteQuietly(tag, numberKey)
            deleteQuietly(tag, totalKey)
            return
        }
        val number = value.substringBefore('/').trim()
        val total: String? = if (value.contains('/')) value.substringAfter('/').trim() else null
        if (number.isNotEmpty()) tag.setField(numberKey, number)
        if (total != null) {
            if (total.isEmpty()) {
                deleteQuietly(tag, totalKey)
            } else {
                tag.setField(totalKey, total)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Cover art
    // ---------------------------------------------------------------------------------------

    private fun removeCover(tag: Tag) {
        try {
            tag.deleteArtworkField()
        } catch (e: Exception) {
            // Nothing to delete.
        }
    }

    private fun setCover(tag: Tag, art: CoverArt, scratchDir: File) {
        when (tag) {
            is FlacTag -> {
                // FLAC needs the picture block built by hand: the generic artwork API would try
                // to decode the image with javax.imageio, which does not exist on Android.
                val size = ImageTools.bounds(art.bytes)
                val width = size?.first ?: 0
                val height = size?.second ?: 0
                val picture = tag.createArtworkField(
                    art.bytes, FRONT_COVER, art.mimeType, "", width, height, 24, 0
                )
                removeCover(tag)
                tag.setField(picture)
            }
            is Mp4Tag -> {
                val field = tag.createArtworkField(art.bytes)
                removeCover(tag)
                tag.setField(field)
            }
            else -> {
                val temp = File.createTempFile("cover_", "." + art.extension, scratchDir)
                try {
                    temp.writeBytes(art.bytes)
                    val artwork = ArtworkFactory.createArtworkFromFile(temp)
                    artwork.setPictureType(FRONT_COVER)
                    removeCover(tag)
                    tag.setField(artwork)
                } finally {
                    temp.delete()
                }
            }
        }
    }
}
