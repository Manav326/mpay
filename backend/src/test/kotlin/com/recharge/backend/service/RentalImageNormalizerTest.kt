package com.recharge.backend.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RentalImageNormalizerTest {
    @Test fun normalizesLargeJpegToSafePersistedSize() {
        val image = BufferedImage(3200, 2200, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val r = (x * 17 + y * 7) and 255
            val g = (x * 3 + y * 19) and 255
            val b = (x * 11 + y * 13) and 255
            image.setRGB(x, y, (r shl 16) or (g shl 8) or b)
        }
        val raw = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        val normalized = RentalImageNormalizer.normalize(raw, "image/jpeg")
        assertEquals("image/jpeg", normalized.contentType)
        assertTrue(normalized.bytes.size.toLong() <= RentalImageNormalizer.MAX_PERSISTED_BYTES)
        val result = ImageIO.read(normalized.bytes.inputStream())
        assertTrue(result.width <= RentalImageNormalizer.MAX_DIMENSION)
        assertTrue(result.height <= RentalImageNormalizer.MAX_DIMENSION)
    }

    @Test fun rejectsSourceAboveHardUploadLimitBeforeDecoding() {
        assertThrows<IllegalArgumentException> {
            RentalImageNormalizer.normalize(ByteArray((RentalImageNormalizer.MAX_INPUT_BYTES + 1).toInt()))
        }
    }
}