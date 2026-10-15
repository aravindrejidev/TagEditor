package com.app.tageditor.data

/**
 * Turns a template such as `%track% - %artist% - %title%` into a safe file name
 * (without extension).
 */
object FileNameTemplate {

    const val DEFAULT = "%track% - %artist% - %title%"

    val placeholders: List<String> = listOf(
        "%track%", "%title%", "%artist%", "%album%", "%albumartist%",
        "%year%", "%genre%", "%disc%", "%composer%", "%filename%"
    )

    private const val MAX_NAME_BYTES = 200

    private val tokenRegex = Regex("%([A-Za-z_]+)%")
    private val removeRegex = Regex("""[*?"<>|\u0000-\u001F]""")
    private val spaceRegex = Regex("""\s+""")
    private val doubleSeparatorRegex = Regex("""(\s*-\s*){2,}""")
    private val yearRegex = Regex("""\d{4}""")

    fun render(template: String, tags: TagData, originalBaseName: String): String {
        val values = mapOf(
            "track" to padNumber(tags.track),
            "title" to tags.title,
            "artist" to tags.artist,
            "album" to tags.album,
            "albumartist" to tags.albumArtist.ifBlank { tags.artist },
            "year" to yearOnly(tags.year),
            "genre" to tags.genre,
            "disc" to numberPart(tags.disc),
            "composer" to tags.composer,
            "filename" to originalBaseName
        )
        val replaced = tokenRegex.replace(template) { match ->
            val value = values[match.groupValues[1].lowercase()]
            if (value == null) match.value else cleanPart(value)
        }
        return finish(replaced)
    }

    private fun numberPart(raw: String): String = raw.substringBefore('/').trim()

    private fun padNumber(raw: String): String {
        val number = numberPart(raw)
        return if (number.isNotEmpty() && number.all { it.isDigit() }) number.padStart(2, '0') else number
    }

    private fun yearOnly(raw: String): String = yearRegex.find(raw)?.value ?: raw.trim()

    private fun cleanPart(raw: String): String {
        val replaced = raw
            .replace('/', '-')
            .replace('\\', '-')
            .replace(":", " -")
        return removeRegex.replace(replaced, "").replace(spaceRegex, " ").trim()
    }

    private fun finish(raw: String): String {
        var out = removeRegex.replace(raw, "")
            .replace('/', '-')
            .replace('\\', '-')
        out = out.replace(spaceRegex, " ").trim()
        out = out.replace(doubleSeparatorRegex, " - ")
        out = out.trim(' ', '-', '_', '.')
        return truncateUtf8(out, MAX_NAME_BYTES).trim(' ', '-', '_', '.')
    }

    private fun truncateUtf8(text: String, maxBytes: Int): String {
        var bytes = 0
        var index = 0
        val builder = StringBuilder()
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val size = when {
                codePoint < 0x80 -> 1
                codePoint < 0x800 -> 2
                codePoint < 0x10000 -> 3
                else -> 4
            }
            if (bytes + size > maxBytes) break
            builder.appendCodePoint(codePoint)
            bytes += size
            index += Character.charCount(codePoint)
        }
        return builder.toString()
    }
}
