package com.app.tageditor.ui

import com.app.tageditor.data.AudioInfo
import java.util.Locale

fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 100 -> String.format(Locale.US, "%.0f MB", mb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
    }
}

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val minutes = seconds / 60
    val rest = seconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, rest)
}

fun formatKhz(hz: Int): String {
    if (hz <= 0) return ""
    return if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", hz / 1000.0)
    }
}

/** Technical facts as (text, highlighted) pairs for the info pills. */
fun infoPills(info: AudioInfo, sizeBytes: Long): List<Pair<String, Boolean>> {
    val pills = ArrayList<Pair<String, Boolean>>()
    if (info.format.isNotEmpty()) pills.add(Pair(info.format, true))
    if (info.lossless) pills.add(Pair("Lossless", true))
    val khz = formatKhz(info.sampleRateHz)
    if (khz.isNotEmpty()) pills.add(Pair(khz, false))
    if (info.bitsPerSample > 0) pills.add(Pair("${info.bitsPerSample}-bit", false))
    if (info.channels.isNotBlank()) pills.add(Pair(info.channels, false))
    if (info.bitRateKbps > 0 && !info.lossless) {
        pills.add(Pair("${info.bitRateKbps} kbps" + if (info.variableBitRate) " VBR" else "", false))
    }
    val duration = formatDuration(info.durationSeconds)
    if (duration.isNotEmpty()) pills.add(Pair(duration, false))
    val size = formatSize(sizeBytes)
    if (size.isNotEmpty()) pills.add(Pair(size, false))
    return pills
}
