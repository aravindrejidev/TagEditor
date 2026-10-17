package com.app.tageditor.data

import android.net.Uri

/** Every tag field the app can edit. */
enum class TagKey {
    TITLE, ARTIST, ALBUM, ALBUM_ARTIST, YEAR, GENRE, TRACK, DISC, COMPOSER, LYRICIST,
    COMMENT, LYRICS, ENCODER, LANGUAGE, BPM, KEY
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
    val lyricist: String = "",
    val comment: String = "",
    val lyrics: String = "",
    val encoder: String = "",
    val language: String = "",
    val bpm: String = "",
    val key: String = ""
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
        TagKey.LYRICIST -> lyricist
        TagKey.COMMENT -> comment
        TagKey.LYRICS -> lyrics
        TagKey.ENCODER -> encoder
        TagKey.LANGUAGE -> language
        TagKey.BPM -> bpm
        TagKey.KEY -> this.key
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
        TagKey.LYRICIST -> copy(lyricist = value)
        TagKey.COMMENT -> copy(comment = value)
        TagKey.LYRICS -> copy(lyrics = value)
        TagKey.ENCODER -> copy(encoder = value)
        TagKey.LANGUAGE -> copy(language = value)
        TagKey.BPM -> copy(bpm = value)
        TagKey.KEY -> copy(key = value)
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

/**
 * Human readable location of a SAF document, for example
 * `/storage/emulated/0/Music/Album/song.flac`. Providers that use opaque ids only get the name.
 */
fun friendlyPath(uri: Uri, fallbackName: String): String {
    val documentId = uri.lastPathSegment ?: return fallbackName
    val colon = documentId.indexOf(':')
    if (colon <= 0) return fallbackName
    val volume = documentId.substring(0, colon)
    val rest = documentId.substring(colon + 1)
    return when {
        rest.isEmpty() -> fallbackName
        volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0/$rest"
        volume.equals("raw", ignoreCase = true) && rest.startsWith("/") -> rest
        Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}").matches(volume) -> "/storage/$volume/$rest"
        else -> fallbackName
    }
}

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
