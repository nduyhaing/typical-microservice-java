package com.shopapp.media;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
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

    /**
     * A valid black-and-white PNG of the given size: one bit per pixel, all zero, so even 20000 x 20000 pixels
     * compress to a few dozen kilobytes (a "decompression bomb").
     */
    public static byte[] hugePng(int width, int height) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
            try (DeflaterOutputStream zip = new DeflaterOutputStream(raw, deflater)) {
                byte[] row = new byte[1 + (width + 7) / 8];
                for (int y = 0; y < height; y++) {
                    zip.write(row);
                }
            } finally {
                deflater.end();
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            png.write(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            ByteBuffer header = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 1).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
            chunk(png, "IHDR", header.array());
            chunk(png, "IDAT", raw.toByteArray());
            chunk(png, "IEND", new byte[0]);
            return png.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(typeBytes);
        out.write(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
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
