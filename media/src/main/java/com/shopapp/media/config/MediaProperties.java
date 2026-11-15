package com.shopapp.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Storage directory ({@code file.directory}, YAS name), browser-facing base URL of this service
 * ({@code shopapp.public-url.media}) and the upload size limit (the Spring multipart limit).
 */
@Component
public class MediaProperties {

    private final String directory;
    private final String publicUrl;
    private final DataSize maxFileSize;

    public MediaProperties(@Value("${file.directory}") String directory,
                           @Value("${shopapp.public-url.media}") String publicUrl,
                           @Value("${spring.servlet.multipart.max-file-size:10MB}") DataSize maxFileSize) {
        this.directory = directory;
        this.publicUrl = publicUrl;
        this.maxFileSize = maxFileSize;
    }

    public String directory() {
        return directory;
    }

    public String publicUrl() {
        return publicUrl;
    }

    public DataSize maxFileSize() {
        return maxFileSize;
    }
}
