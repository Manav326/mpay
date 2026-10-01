package com.recharge.client.core.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

data class RentalPhotoPayload(val bytes: ByteArray, val contentType: String = "image/jpeg", val fileName: String)

object RentalPhotoCompressor {
    private const val MAX_INPUT_BYTES = 15L * 1024L * 1024L
    private const val TARGET_BYTES = 4_750_000L
    private const val MAX_PIXELS = 40_000_000L
    private const val MAX_DIMENSION = 2_560
    private val qualitySteps = intArrayOf(92,86,80,74,68,62,56,50,44,38)
    private val dimensionSteps = intArrayOf(2560,2304,2048,1920,1800,1600,1440,1280,1024)

    fun compress(resolver: ContentResolver, uri: Uri, prefix: String): RentalPhotoPayload {
        val rawSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        require(rawSize <= MAX_INPUT_BYTES || rawSize < 0) { "Photo is too large. Maximum source size is 15 MB." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: error("Unable to read selected photo")
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0) { "The selected file is not a readable image." }
        require(width.toLong() * height.toLong() <= MAX_PIXELS) { "Image dimensions are too large. Maximum is 40 megapixels." }
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSample(width, height)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("Unable to decode selected photo")
        bitmap = applyExifOrientation(resolver, uri, bitmap)
        var lastBytes: ByteArray? = null
        for (maxDimension in dimensionSteps) {
            val scaled = scaleTo(bitmap, maxDimension)
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
            for (quality in qualitySteps) {
                val output = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "The selected photo could not be encoded." }
                val bytes = output.toByteArray()
                lastBytes = bytes
                if (bytes.size.toLong() <= TARGET_BYTES) {
                    bitmap.recycle()
                    return RentalPhotoPayload(bytes, "image/jpeg", prefix + ".jpg")
                }
            }
        }
        bitmap.recycle()
        val bytes = lastBytes ?: error("The selected photo could not be encoded.")
        require(bytes.size.toLong() <= 5L * 1024L * 1024L) { "The selected photo could not be compressed below 5 MB. Please choose another image." }
        return RentalPhotoPayload(bytes, "image/jpeg", prefix + ".jpg")
    }

    private fun calculateSample(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > MAX_DIMENSION * 1.5) sample *= 2
        return sample
    }

    private fun scaleTo(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(bitmap, maxOf(1,(bitmap.width * scale).toInt()), maxOf(1,(bitmap.height * scale).toInt()), true)
    }

    private fun applyExifOrientation(resolver: ContentResolver, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f,1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f,-1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f,1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f,1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return runCatching { Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true) }.getOrElse { bitmap }
    }
}