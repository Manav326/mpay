package com.recharge.backend.service

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import javax.imageio.ImageIO

class ImageVariantSupportTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun rebuildsCorruptExistingLegacyVariant() {
        val source = tempDir.resolve("legacy.png")
        val image = BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB)
        Files.newOutputStream(source).use { ImageIO.write(image, "png", it) }

        val target = ImageVariantSupport.ensureVariant(tempDir, source.fileName.toString(), ImageVariant.THUMB)
        assertNotNull(ImageIO.read(target.toFile()))

        Files.writeString(target, "corrupt legacy variant")
        val repaired = ImageVariantSupport.ensureVariant(tempDir, source.fileName.toString(), ImageVariant.THUMB)

        val decoded = ImageIO.read(repaired.toFile())
        assertNotNull(decoded)
        assertTrue(repaired.toFile().length() > 0L)
    }

    @Test
    fun rebuildsStaleLegacyVariantWhenSourceIsNewer() {
        val source = tempDir.resolve("legacy.jpg")
        val image = BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB)
        Files.newOutputStream(source).use { ImageIO.write(image, "jpg", it) }

        val target = ImageVariantSupport.ensureVariant(tempDir, source.fileName.toString(), ImageVariant.LARGE)
        val oldTime = Files.getLastModifiedTime(source).toMillis() - 2_000L
        Files.setLastModifiedTime(target, FileTime.fromMillis(oldTime))

        val repaired = ImageVariantSupport.ensureVariant(tempDir, source.fileName.toString(), ImageVariant.LARGE)
        assertNotNull(ImageIO.read(repaired.toFile()))
        assertTrue(Files.getLastModifiedTime(repaired).toMillis() >= Files.getLastModifiedTime(source).toMillis())
    }
}
