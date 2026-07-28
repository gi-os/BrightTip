package com.gios.lighttip.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File

object ImageUtils {

    /** Rotate upright per EXIF so the pixels we store match what the model reads. */
    fun normalizeUpright(bytes: ByteArray): Bitmap {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /**
     * Receipts are long and thin and the model only needs legible glyphs, so cap the
     * long edge. A 4000px photo costs several times the tokens for no extra accuracy.
     */
    fun downscaled(src: Bitmap, maxEdge: Int = 1600): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    fun saveJpeg(bmp: Bitmap, file: File, quality: Int = 88): File {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return file
    }
}
