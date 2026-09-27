package com.recharge.backend.service

import org.springframework.stereotype.Service
import java.util.Locale

data class RentalPhotoResource(
    val slot: Int? = null,
    val url: String,
    val thumbnailUrl: String,
    val largeUrl: String
)

@Service
class RentalPhotoService {
    private val keyPattern = Regex("[A-Za-z0-9._-]+")

    fun vehiclePhotos(imageUrl: String?): List<RentalPhotoResource> =
        references(imageUrl)
            .take(4)
            .mapIndexed { index, value -> resource(value, index) }

    fun driverPhoto(photoUrl: String?): RentalPhotoResource? =
        references(photoUrl).firstOrNull()?.let { resource(it, null) }

    fun normalizeStoredReferences(value: String?): String? {
        if (value.isNullOrBlank()) return null

        val references = value
            .replace("\\n", "|")
            .split(Regex("[|,]"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(4)

        if (references.isEmpty()) return null

        return references.joinToString("|") { reference ->
            canonicalStoredReference(reference)
                ?: throw IllegalArgumentException(
                    "Vehicle photos must use mPay's stored photo references"
                )
        }
    }

    fun references(value: String?): List<String> =
        value.orEmpty()
            .replace("\\n", "|")
            .split(Regex("[|,]"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(4)

    private fun resource(reference: String, slot: Int?): RentalPhotoResource {
        val trimmed = reference.trim()
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            return RentalPhotoResource(
                slot = slot,
                url = trimmed,
                thumbnailUrl = trimmed,
                largeUrl = trimmed
            )
        }

        val canonical = canonicalStoredReference(trimmed)
            ?: throw IllegalArgumentException("Invalid rental photo reference")

        return RentalPhotoResource(
            slot = slot,
            url = canonical,
            thumbnailUrl = variantUrl(canonical, "thumb"),
            largeUrl = variantUrl(canonical, "large")
        )
    }

    private fun canonicalStoredReference(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        val key = when {
            trimmed.startsWith(RENTAL_PHOTO_URL_PREFIX) ->
                trimmed.removePrefix(RENTAL_PHOTO_URL_PREFIX)
            trimmed.startsWith("/api/") ->
                return null
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) ->
                return null
            else -> trimmed.trimStart('/')
        }

        return key.takeIf { keyPattern.matches(it) }?.let { RENTAL_PHOTO_URL_PREFIX + it }
    }

    private fun variantUrl(url: String, variant: String): String =
        url + (if (url.contains("?")) "&" else "?") + "variant=" + variant

    companion object {
        const val RENTAL_PHOTO_URL_PREFIX = "/api/v1/car-rental/photos/"
    }
}
