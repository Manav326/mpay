package com.recharge.backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

interface ProfileImageStorage {
    data class StoredImage(
        val key: String,
        val contentType: String,
        val resource: FileSystemResource,
        val size: Long,
        val lastModified: Instant
    )

    fun save(publicUserId: String, file: MultipartFile): String
    fun load(key: String, variant: ImageVariant = ImageVariant.AVATAR): StoredImage?
    fun delete(key: String?)
}

@Service
class LocalProfileImageStorage(
    @Value("\${app.profile.images-dir:./data/profile-images}") imagesDir: String
) : ProfileImageStorage {
    private val root: Path = Paths.get(imagesDir).toAbsolutePath().normalize().also { Files.createDirectories(it) }

    override fun save(publicUserId: String, file: MultipartFile): String {
        val contentType = file.contentType?.lowercase().orEmpty()
        require(contentType in allowedContentTypes) { "Only JPEG, PNG or WebP images are supported" }
        require(!file.isEmpty) { "Profile image is empty" }
        require(file.size <= MAX_BYTES) { "Profile image must be 5 MB or smaller" }

        val ext = when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val key = "${publicUserId}_${UUID.randomUUID()}.$ext"
        Files.write(resolve(key), file.bytes)
        runCatching { ImageVariantSupport.ensureVariant(root, key, ImageVariant.AVATAR) }
        return key
    }

    override fun load(key: String, variant: ImageVariant): ProfileImageStorage.StoredImage? {
        val original = runCatching { resolve(key) }.getOrNull() ?: return null
        if (!Files.exists(original) || !Files.isRegularFile(original)) return null

        val target = if (variant == ImageVariant.MEDIUM) {
            ImageVariantSupport.ensureVariant(root, key, variant)
        } else {
            ImageVariantSupport.ensureVariant(root, key, variant)
        }

        val contentType = "image/jpeg"
        val resource = FileSystemResource(target.toFile())
        if (!resource.exists() || !resource.isReadable) return null
        return ProfileImageStorage.StoredImage(
            key = key,
            contentType = contentType,
            resource = resource,
            size = resource.contentLength(),
            lastModified = Instant.ofEpochMilli(resource.lastModified())
        )
    }

    override fun delete(key: String?) {
        if (key.isNullOrBlank()) return
        runCatching { Files.deleteIfExists(resolve(key)) }
        ImageVariantSupport.deleteVariants(root, key)
    }

    private fun resolve(key: String): Path {
        require(key.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid image key" }
        val resolved = root.resolve(key).normalize()
        require(resolved.parent == root) { "Invalid image path" }
        return resolved
    }

    companion object {
        private const val MAX_BYTES = 5L * 1024L * 1024L
        private val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
    }
}
