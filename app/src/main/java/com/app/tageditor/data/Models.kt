package com.app.tageditor.data

import android.net.Uri

/** Every tag field the app can edit. */
enum class TagKey {
    TITLE, ARTIST, ALBUM, ALBUM_ARTIST, YEAR, GENRE, TRACK, DISC, COMPOSER, COMMENT, LYRICS
}

/** Editable tag values of one audio file. Track/disc use "3" or "3/12" notation. */
data class TagData(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val genre: String = "",
    val track: String = "",
    val disc: String = "",
    val composer: String = "",
    val comment: String = "",
    val lyrics: String = ""
) {
    operator fun get(key: TagKey): String = when (key) {
        TagKey.TITLE -> title
        TagKey.ARTIST -> artist
        TagKey.ALBUM -> album
        TagKey.ALBUM_ARTIST -> albumArtist
        TagKey.YEAR -> year
        TagKey.GENRE -> genre
        TagKey.TRACK -> track
        TagKey.DISC -> disc
        TagKey.COMPOSER -> composer
        TagKey.COMMENT -> comment
        TagKey.LYRICS -> lyrics
    }

    fun withValue(key: TagKey, value: String): TagData = when (key) {
        TagKey.TITLE -> copy(title = value)
        TagKey.ARTIST -> copy(artist = value)
        TagKey.ALBUM -> copy(album = value)
        TagKey.ALBUM_ARTIST -> copy(albumArtist = value)
        TagKey.YEAR -> copy(year = value)
        TagKey.GENRE -> copy(genre = value)
        TagKey.TRACK -> copy(track = value)
        TagKey.DISC -> copy(disc = value)
        TagKey.COMPOSER -> copy(composer = value)
        TagKey.COMMENT -> copy(comment = value)
        TagKey.LYRICS -> copy(lyrics = value)
    }

    fun isEmpty(): Boolean = TagKey.values().all { this[it].isBlank() }
}

/** Fields whose trimmed value differs between two snapshots. A blank value means "delete this tag". */
fun diffTags(original: TagData, edited: TagData): Map<TagKey, String> {
    val result = LinkedHashMap<TagKey, String>()
    for (key in TagKey.values()) {
        val before = original[key].trim()
        val after = edited[key].trim()
        if (before != after) result[key] = after
    }
    return result
}

/** Technical (read-only) facts about the audio stream. */
data class AudioInfo(
    val format: String = "",
    val encoding: String = "",
    val durationSeconds: Int = 0,
    val bitRateKbps: Long = 0L,
    val sampleRateHz: Int = 0,
    val channels: String = "",
    val bitsPerSample: Int = 0,
    val lossless: Boolean = false,
    val variableBitRate: Boolean = false
)

/** Embedded cover image (JPEG or PNG bytes). */
class CoverArt(val bytes: ByteArray, val mimeType: String) {
    val extension: String
        get() = when {
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("gif", ignoreCase = true) -> "gif"
            mimeType.contains("bmp", ignoreCase = true) -> "bmp"
            else -> "jpg"
        }
}

/** What should happen with the embedded cover when saving. */
sealed interface CoverChange {
    object Keep : CoverChange
    object Remove : CoverChange
    class Replace(val art: CoverArt) : CoverChange
}

data class ParsedAudio(
    val tags: TagData,
    val info: AudioInfo,
    val cover: CoverArt?
)

/** One audio document picked through the Storage Access Framework. */
data class AudioItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long = 0L,
    val folder: String = ""
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val baseName: String
        get() = name.substringBeforeLast('.', name)
}

data class RenamedDoc(val uri: Uri, val name: String)

/** Compares strings so that "Track 2" sorts before "Track 10". */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ei = i
                while (ei < a.length && a[ei].isDigit()) ei++
                var ej = j
                while (ej < b.length && b[ej].isDigit()) ej++
                val na = a.substring(i, ei).trimStart('0')
                val nb = b.substring(j, ej).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
