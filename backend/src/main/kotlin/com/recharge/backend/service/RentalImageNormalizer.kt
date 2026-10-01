package com.recharge.backend.service

import net.coobird.thumbnailator.Thumbnails
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

data class NormalizedRentalImage(
    val bytes: ByteArray,
    val contentType: String
)

object RentalImageNormalizer {
    const val MAX_INPUT_BYTES = 15L * 1024L * 1024L
    const val MAX_PERSISTED_BYTES = 5L * 1024L * 1024L
    const val TARGET_BYTES = 4_750_000L
    const val MAX_PIXELS = 40_000_000L
    const val MAX_DIMENSION = 2_560

    private val allowedFormats = setOf("jpeg", "jpg", "png", "webp")
    private val targetSizes = intArrayOf(2560, 2304, 2048, 1920, 1800, 1600, 1440, 1280, 1024)
    private val qualitySteps = doubleArrayOf(0.88, 0.82, 0.76, 0.70, 0.64, 0.58, 0.52, 0.46, 0.40)

    fun normalize(bytes: ByteArray, declaredContentType: String? = null): NormalizedRentalImage {
        require(bytes.isNotEmpty()) { "Photo is empty." }
        require(bytes.size.toLong() <= MAX_INPUT_BYTES) { "Photo is too large. Maximum upload size is 15 MB." }
        val metadata = readMetadata(bytes)
        require(metadata.format in allowedFormats) { "Only JPEG, PNG or WebP images are supported." }
        if (metadata.format in setOf("jpeg", "jpg") && bytes.size.toLong() <= TARGET_BYTES && metadata.width <= MAX_DIMENSION && metadata.height <= MAX_DIMENSION) {
            return NormalizedRentalImage(bytes, "image/jpeg")
        }
        var lastOutput: ByteArray? = null
        for (size in targetSizes) {
            for (quality in qualitySteps) {
                val encoded = encodeJpeg(bytes, size, quality)
                lastOutput = encoded
                if (encoded.size.toLong() <= TARGET_BYTES) return NormalizedRentalImage(encoded, "image/jpeg")
            }
        }
        val result = requireNotNull(lastOutput)
        require(result.size.toLong() <= MAX_PERSISTED_BYTES) { "Photo could not be compressed below 5 MB. Please choose a simpler image." }
        return NormalizedRentalImage(result, "image/jpeg")
    }

    private data class Metadata(val width: Int, val height: Int, val format: String)

    private fun readMetadata(bytes: ByteArray): Metadata {
        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes)) ?: throw IllegalArgumentException("The image could not be read.")
        input.use {
            val reader = ImageIO.getImageReaders(it).asSequence().firstOrNull() ?: throw IllegalArgumentException("The image format is not supported.")
            try {
                reader.setInput(input, true, true)
                val width = runCatching { reader.getWidth(0) }.getOrElse { throw IllegalArgumentException("The image dimensions could not be read.") }
                val height = runCatching { reader.getHeight(0) }.getOrElse { throw IllegalArgumentException("The image dimensions could not be read.") }
                require(width > 0 && height > 0 && width.toLong() * height.toLong() <= MAX_PIXELS) { "Image dimensions are too large. Maximum is 40 megapixels." }
                return Metadata(width, height, reader.formatName.lowercase())
            } finally { reader.dispose() }
        }
    }

    private fun encodeJpeg(bytes: ByteArray, maxDimension: Int, quality: Double): ByteArray {
        val output = ByteArrayOutputStream()
        Thumbnails.of(ByteArrayInputStream(bytes)).size(maxDimension, maxDimension).keepAspectRatio(true).useExifOrientation(true).outputFormat("jpg").outputQuality(quality).toOutputStream(output)
        return output.toByteArray()
    }
}