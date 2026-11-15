package com.shopapp.media.utils;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Finds an upload's real image type by decoding it with ImageIO; the file name and the client's Content-Type are
 * never trusted. Only JPEG, PNG and GIF are accepted. Huge dimensions are refused before decoding and the pixels are
 * decoded subsampled, so a small "decompression bomb" file cannot exhaust the heap.
 */
public final class ImageTypeDetector {

    /** Allowed image type: its MIME type and the extension of the stored file. */
    public record ImageType(String mediaType, String extension) {
    }

    private static final Map<String, ImageType> ALLOWED = Map.of(
        "png", new ImageType("image/png", "png"),
        "jpeg", new ImageType("image/jpeg", "jpg"),
        "gif", new ImageType("image/gif", "gif"));

    private static final int MAX_SIDE = 20_000;
    private static final long DECODED_PIXEL_BUDGET = 1_000_000L;

    private ImageTypeDetector() {
    }

    /**
     * The decoded image's type, or empty when the bytes are not a complete JPEG, PNG or GIF image.
     */
    public static Optional<ImageType> detect(byte[] content) {
        if (content == null || content.length == 0) {
            return Optional.empty();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                return Optional.empty();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                ImageType type = ALLOWED.get(reader.getFormatName().toLowerCase(Locale.ROOT));
                if (type == null) {
                    return Optional.empty();
                }
                reader.setInput(input, true, true);
                return decodes(reader) ? Optional.of(type) : Optional.empty();
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            // ImageIO reports corrupt data with IOException or unchecked exceptions alike
            return Optional.empty();
        }
    }

    private static boolean decodes(ImageReader reader) throws IOException {
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) {
            return false;
        }
        int step = 1;
        while ((long) (width / step) * (height / step) > DECODED_PIXEL_BUDGET) {
            step++;
        }
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(step, step, 0, 0);
        BufferedImage image = reader.read(0, param);
        return image != null;
    }
}
