package com.recharge.backend.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

interface RentalImageStorage {
    data class StoredImage(val key: String, val contentType: String, val bytes: ByteArray)

    fun save(carId: Long, slot: Int, file: MultipartFile): String
    fun saveDriverPhoto(driverId: Long, file: MultipartFile): String
    fun load(key: String): StoredImage?
    fun delete(key: String?)
}

@Service
class LocalRentalImageStorage(
    @Value("\${app.rental.images-dir:./data/rental-images}") imagesDir: String
) : RentalImageStorage {
    private val root: Path = Paths.get(imagesDir).toAbsolutePath().normalize().also { Files.createDirectories(it) }

    override fun save(carId: Long, slot: Int, file: MultipartFile): String {
        require(slot in 0..3) { "Vehicle photo slot must be between 0 and 3" }
        require(!file.isEmpty) { "Vehicle photo is empty" }

        val contentType = file.contentType?.lowercase().orEmpty()
        require(contentType in allowedContentTypes) { "Only JPEG, PNG or WebP images are supported" }
        require(file.size <= MAX_BYTES) { "Vehicle photo must be 5 MB or smaller" }

        val ext = when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val key = "rental_${carId}_${slot}_${UUID.randomUUID()}.$ext"
        Files.write(resolve(key), file.bytes)
        return key
    }

    override fun saveDriverPhoto(driverId: Long, file: MultipartFile): String {
        require(!file.isEmpty) { "Driver photo is empty" }

        val contentType = file.contentType?.lowercase().orEmpty()
        require(contentType in allowedContentTypes) { "Only JPEG, PNG or WebP images are supported" }
        require(file.size <= MAX_BYTES) { "Driver photo must be 5 MB or smaller" }

        val ext = when (contentType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val key = "rental_driver_${driverId}_${UUID.randomUUID()}.$ext"
        Files.write(resolve(key), file.bytes)
        return key
    }

    override fun load(key: String): RentalImageStorage.StoredImage? {
        val path = runCatching { resolve(key) }.getOrNull() ?: return null
        if (!Files.exists(path) || !Files.isRegularFile(path)) return null

        val contentType = when (path.fileName.toString().substringAfterLast('.', "jpg").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        return RentalImageStorage.StoredImage(key, contentType, Files.readAllBytes(path))
    }

    override fun delete(key: String?) {
        if (key.isNullOrBlank()) return
        runCatching { Files.deleteIfExists(resolve(key)) }
    }

    private fun resolve(key: String): Path {
        require(key.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid rental image key" }
        val resolved = root.resolve(key).normalize()
        require(resolved.parent == root) { "Invalid rental image path" }
        return resolved
    }

    companion object {
        private const val MAX_BYTES = 5L * 1024L * 1024L
        private val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
    }
}
