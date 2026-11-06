package com.app.tageditor.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** What kind of lyrics a piece of text holds. */
enum class LyricsFormat(val label: String) {
    PLAIN("Plain text"),
    LRC("LRC"),
    ELRC("Enhanced LRC"),
    TTML("TTML")
}

/** A lyrics file that was read from storage. [text] is stored in the tag exactly as it is. */
data class LyricsFile(val name: String, val text: String, val format: LyricsFormat)

/** Format and size of the lyrics currently in the editor, for the info pills. */
data class LyricsSummary(val format: LyricsFormat, val lines: Int)

/**
 * Reading and recognising lyrics files (LRC, enhanced LRC, TTML, plain text). The content is not
 * converted: a synced file is embedded in the tag with its timestamps untouched.
 */
object LyricsFiles {

    const val MAX_BYTES = 2 * 1024 * 1024

    private val extensions = setOf("lrc", "elrc", "ttml", "xml", "dfxp", "txt")
    private val xmlExtensions = setOf("ttml", "xml", "dfxp")

    /** [00:12.34], [00:12.340], [00:12:34] or [1:02] */
    private val lineStamp = Regex("""^\s*\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\]""")

    /** <00:12.34> word timing used by enhanced LRC */
    private val wordStamp = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")

    private val ttmlRoot = Regex("""<(?:[\w.-]+:)?tt[\s>]""")
    private val ttmlParagraph = Regex("""<(?:[\w.-]+:)?p[\s>]""")

    fun isSupported(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in extensions

    /** Decodes the bytes of a lyrics file (UTF-8 / UTF-16 with or without BOM) and tidies line breaks. */
    fun decode(bytes: ByteArray): String {
        val raw = when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> decodeUtf8OrLegacy(bytes)
        }
        return raw.replace("\r\n", "\n").replace('\r', '\n').trim()
    }

    private fun decodeUtf8OrLegacy(bytes: ByteArray): String {
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            // Old LRC files are often saved in a Windows code page instead of UTF-8.
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    fun detect(text: String): LyricsFormat {
        val head = text.trimStart()
        if (head.startsWith("<") && ttmlRoot.containsMatchIn(head.take(4000))) {
            return LyricsFormat.TTML
        }
        val stamped = text.lineSequence().any { lineStamp.containsMatchIn(it) }
        if (stamped) {
            return if (wordStamp.containsMatchIn(text)) LyricsFormat.ELRC else LyricsFormat.LRC
        }
        return LyricsFormat.PLAIN
    }

    fun analyse(text: String): LyricsSummary? {
        if (text.isBlank()) return null
        val format = detect(text)
        val lines = when (format) {
            LyricsFormat.TTML -> ttmlParagraph.findAll(text).count()
            LyricsFormat.LRC, LyricsFormat.ELRC -> text.lineSequence().count { lineStamp.containsMatchIn(it) }
            LyricsFormat.PLAIN -> text.lineSequence().count { it.isNotBlank() }
        }
        return LyricsSummary(format, lines)
    }

    /** Turns the raw bytes of a picked file into a [LyricsFile] or explains why it cannot be used. */
    fun parse(name: String, bytes: ByteArray): LyricsFile {
        val text = decode(bytes)
        if (text.isBlank()) throw IllegalArgumentException("The lyrics file is empty")
        val format = detect(text)
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in xmlExtensions && format != LyricsFormat.TTML) {
            throw IllegalArgumentException("This XML file is not a TTML lyrics file")
        }
        return LyricsFile(name, text, format)
    }
}
