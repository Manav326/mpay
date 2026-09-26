package com.recharge.backend.service

import net.coobird.thumbnailator.Thumbnails
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

enum class ImageVariant(
    val width: Int,
    val quality: Double
) {
    THUMB(512, 0.78),
    AVATAR(256, 0.80),
    MEDIUM(1024, 0.82),
    LARGE(1600, 0.84);

    companion object {
        fun parse(value: String?): ImageVariant = when (value?.trim()?.lowercase(Locale.ROOT)) {
            "avatar" -> AVATAR
            "medium" -> MEDIUM
            "large" -> LARGE
            else -> THUMB
        }
    }
}

object ImageVariantSupport {
    fun ensureVariant(root: Path, key: String, variant: ImageVariant): Path {
        val source = root.resolve(key).normalize()
        require(source.parent == root) { "Invalid image path" }
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            throw IllegalArgumentException("Image not found")
        }

        val target = root.resolve("${key}.${variant.name.lowercase(Locale.ROOT)}.jpg").normalize()
        require(target.parent == root) { "Invalid image path" }

        if (Files.exists(target) && Files.isRegularFile(target)) return target

        val temp = Files.createTempFile(root, ".image-", ".tmp")
        try {
            Thumbnails.of(source.toFile())
                .size(variant.width, variant.width)
                .keepAspectRatio(true)
                .useExifOrientation(true)
                .outputFormat("jpg")
                .outputQuality(variant.quality)
                .toFile(temp.toFile())

            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        } catch (error: Exception) {
            runCatching { Files.deleteIfExists(temp) }
            throw IllegalArgumentException("Unable to process image", error)
        }
    }

    fun deleteVariants(root: Path, key: String) {
        ImageVariant.entries.forEach { variant ->
            runCatching {
                Files.deleteIfExists(
                    root.resolve("${key}.${variant.name.lowercase(Locale.ROOT)}.jpg").normalize()
                )
            }
        }
    }
}
