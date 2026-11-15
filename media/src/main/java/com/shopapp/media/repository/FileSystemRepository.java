package com.shopapp.media.repository;

import com.shopapp.media.config.MediaProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * Image files in the media directory ({@code file.directory}). Every name is resolved against that directory and
 * must stay inside it: names with path separators or "..", or that normalize to a path outside, are refused.
 */
@Slf4j
@Repository
public class FileSystemRepository {

    private final Path baseDir;

    public FileSystemRepository(MediaProperties properties) {
        this.baseDir = Path.of(properties.directory()).toAbsolutePath().normalize();
    }

    /**
     * Writes a new file (never overwrites) and returns its name relative to the media directory.
     */
    public String persistFile(String fileName, byte[] content) {
        Path path = resolve(fileName);
        try {
            Files.createDirectories(baseDir);
            Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store file " + fileName, ex);
        }
        log.info("File saved: {}", fileName);
        return fileName;
    }

    /**
     * The path of an existing regular file in the media directory, or null when there is none.
     */
    public Path getFile(String fileName) {
        Path path = resolve(fileName);
        return Files.isRegularFile(path) ? path : null;
    }

    /**
     * Deletes the file if it exists.
     */
    public void deleteFile(String fileName) {
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete file " + fileName, ex);
        }
    }

    Path resolve(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/")
            || fileName.contains("\\") || fileName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid file name");
        }
        Path path = baseDir.resolve(fileName).normalize();
        if (!path.startsWith(baseDir) || path.equals(baseDir)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return path;
    }
}
