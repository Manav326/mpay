package com.recharge.backend.service

import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import javax.imageio.ImageIO

data class ImportedRentalImage(
    val bytes: ByteArray,
    val contentType: String
)

@Service
class RentalPhotoImportService {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun importFromUrl(rawUrl: String): ImportedRentalImage {
        var uri = validateUri(rawUrl)
        repeat(MAX_REDIRECTS + 1) { attempt ->
            val request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.8")
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { body ->
                if (response.statusCode() in 300..399) {
                    val location = response.headers().firstValue("Location")
                        .orElseThrow { IllegalArgumentException("The image URL redirected without a destination") }
                    if (attempt == MAX_REDIRECTS) {
                        throw IllegalArgumentException("The image URL redirected too many times")
                    }
                    uri = validateUri(uri.resolve(location).toString())
                    return@use
                }

                if (response.statusCode() !in 200..299) {
                    throw IllegalArgumentException("The image URL could not be loaded (HTTP ${response.statusCode()})")
                }

                val declaredContentType = response.headers().firstValue("Content-Type")
                    .orElse("")
                    .substringBefore(';')
                    .trim()
                    .lowercase(Locale.ROOT)
                if (declaredContentType == "text/html" || declaredContentType == "application/xhtml+xml") {
                    throw IllegalArgumentException(
                        "This URL opens a webpage, not an image. Use the direct image URL instead."
                    )
                }

                val declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
                require(declaredLength <= MAX_BYTES || declaredLength < 0) {
                    "The image at this URL must be 5 MB or smaller"
                }

                return validateImage(readAtMost(body, MAX_BYTES))
            }
        }
        throw IllegalArgumentException("The image URL could not be loaded")
    }

    private fun validateUri(rawUrl: String): URI {
        val value = rawUrl.trim()
        require(value.length in 1..2048) { "Enter a valid image URL" }

        val uri = runCatching { URI(value) }
            .getOrElse { throw IllegalArgumentException("Enter a valid image URL") }

        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
            "Image URL must use HTTP or HTTPS"
        }
        require(uri.userInfo.isNullOrBlank()) { "Image URL must not contain embedded credentials" }
        require(!uri.host.isNullOrBlank()) { "Enter a valid image URL" }

        val addresses = runCatching { InetAddress.getAllByName(uri.host) }
            .getOrElse { throw IllegalArgumentException("The image URL host could not be resolved") }

        require(addresses.isNotEmpty() && addresses.all(::isPublicAddress)) {
            "This image URL points to a private or local address"
        }

        return uri
    }

    private fun isPublicAddress(address: InetAddress): Boolean =
        !address.isAnyLocalAddress &&
            !address.isLoopbackAddress &&
            !address.isLinkLocalAddress &&
            !address.isSiteLocalAddress &&
            !address.isMulticastAddress

    private fun readAtMost(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "The image at this URL must be 5 MB or smaller" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun validateImage(bytes: ByteArray): ImportedRentalImage {
        require(bytes.isNotEmpty()) { "The image URL returned an empty file" }

        val input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("The image could not be read")

        input.use {
            val reader = ImageIO.getImageReaders(it).asSequence().firstOrNull()
                ?: throw IllegalArgumentException("The URL does not contain a supported image")
            val format = reader.formatName.lowercase()
            reader.setInput(input, true, true)
            val width = runCatching { reader.getWidth(0) }.getOrElse {
                reader.dispose()
                throw IllegalArgumentException("The image dimensions could not be read")
            }
            val height = runCatching { reader.getHeight(0) }.getOrElse {
                reader.dispose()
                throw IllegalArgumentException("The image dimensions could not be read")
            }
            val pixels = width.toLong() * height.toLong()
            require(width > 0 && height > 0 && pixels <= MAX_PIXELS) {
                "The image dimensions are too large. Use an image of 25 megapixels or less."
            }
            reader.dispose()
            val contentType = when (format) {
                "jpeg", "jpg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> throw IllegalArgumentException("Only JPEG, PNG or WebP images are supported")
            }
            require(ImageIO.read(ByteArrayInputStream(bytes)) != null) {
                "The URL does not contain a readable image"
            }
            return ImportedRentalImage(bytes, contentType)
        }
    }

    companion object {
        private const val MAX_BYTES = 5L * 1024L * 1024L
        private const val MAX_REDIRECTS = 3
        private const val MAX_PIXELS = 25_000_000L
        private const val USER_AGENT = "mPay-RentalPhotoImporter/1.0"
        private val REQUEST_TIMEOUT = Duration.ofSeconds(12)
    }
}
