package com.shopapp.media;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Small generated images and file-system helpers for the media tests.
 */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] image(String format) {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, Color.RED.getRGB());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No writer for " + format);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    public static Path tempDirectory() {
        try {
            return Files.createTempDirectory("media-it");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static long fileCount(Path dir) {
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
