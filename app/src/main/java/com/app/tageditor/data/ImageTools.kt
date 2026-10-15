package com.app.tageditor.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Small helpers for cover images. Everything here works on raw bytes. */
object ImageTools {

    /** Detects JPEG / PNG / GIF / BMP from the file signature. */
    fun sniffMime(data: ByteArray): String? {
        fun at(index: Int): Int = if (index < data.size) data[index].toInt() and 0xFF else -1
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png"
            at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 -> "image/gif"
            at(0) == 0x42 && at(1) == 0x4D -> "image/bmp"
            else -> null
        }
    }

    /** Width x height without decoding the pixels, or null if the data is not an image. */
    fun bounds(data: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            Pair(options.outWidth, options.outHeight)
        } else {
            null
        }
    }

    /** Decodes a down-sampled bitmap whose longest side is at least [maxSide] (when possible). */
    fun decodeSampled(data: ByteArray, maxSide: Int): Bitmap? {
        val size = bounds(data) ?: return null
        val longest = max(size.first, size.second)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    /**
     * Prepares a picked image for embedding. JPEG/PNG files are used untouched when
     * [keepOriginal] is set or when they already fit into [maxPx]; everything else is scaled
     * down and stored as JPEG.
     */
    fun prepareCover(raw: ByteArray, maxPx: Int, keepOriginal: Boolean): CoverArt {
        val size = bounds(raw) ?: throw IOException("This file is not a supported image")
        val longest = max(size.first, size.second)
        val mime = sniffMime(raw)
        val embeddable = mime == "image/jpeg" || mime == "image/png"
        if (mime != null && embeddable && (keepOriginal || longest <= maxPx)) {
            return CoverArt(raw, mime)
        }

        val decoded = decodeSampled(raw, maxPx) ?: throw IOException("Could not decode the image")
        var bitmap: Bitmap = decoded
        val scale = maxPx.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) {
            val width = (decoded.width * scale).roundToInt().coerceAtLeast(1)
            val height = (decoded.height * scale).roundToInt().coerceAtLeast(1)
            bitmap = Bitmap.createScaledBitmap(decoded, width, height, true)
        }
        if (bitmap.hasAlpha()) {
            // JPEG has no alpha channel: flatten onto white instead of getting black corners.
            val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(flat)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            bitmap = flat
        }
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
            throw IOException("Could not encode the image")
        }
        return CoverArt(out.toByteArray(), "image/jpeg")
    }
}
