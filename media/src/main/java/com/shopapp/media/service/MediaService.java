package com.shopapp.media.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.media.config.MediaProperties;
import com.shopapp.media.model.Media;
import com.shopapp.media.repository.FileSystemRepository;
import com.shopapp.media.repository.MediaRepository;
import com.shopapp.media.utils.FileNames;
import com.shopapp.media.utils.ImageTypeDetector;
import com.shopapp.media.utils.ImageTypeDetector.ImageType;
import com.shopapp.media.viewmodel.MediaPostVm;
import com.shopapp.media.viewmodel.MediaVm;
import com.shopapp.media.viewmodel.NoFileMediaVm;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    static final String INVALID_FILE_TYPE = "File type not allowed. Allowed types are: JPEG, PNG, GIF";
    static final String MEDIA_NOT_FOUND = "Media %s is not found";

    private final MediaRepository mediaRepository;
    private final FileSystemRepository fileSystemRepository;
    private final MediaProperties properties;

    /**
     * Stores an uploaded image under a server-generated name; its type comes from decoding the content.
     */
    public NoFileMediaVm saveMedia(MediaPostVm mediaPostVm) {
        MultipartFile file = mediaPostVm.multipartFile();
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BadRequestException("File is larger than " + properties.maxFileSize().toMegabytes() + " MB");
        }
        byte[] content = readBytes(file);
        ImageType type = ImageTypeDetector.detect(content)
            .orElseThrow(() -> new BadRequestException(INVALID_FILE_TYPE));

        String displayName = FileNames.withExtension(FileNames.sanitize(
            StringUtils.hasText(mediaPostVm.fileNameOverride())
                ? mediaPostVm.fileNameOverride() : file.getOriginalFilename()),
            type.extensions(), type.extension());
        String storedName = fileSystemRepository.persistFile(UUID.randomUUID() + "." + type.extension(), content);

        Media media = new Media();
        media.setCaption(mediaPostVm.caption());
        media.setFileName(displayName);
        media.setFilePath(storedName);
        media.setMediaType(type.mediaType());
        try {
            Media saved = mediaRepository.save(media);
            return new NoFileMediaVm(saved.getId(), saved.getCaption(), saved.getFileName(), saved.getMediaType());
        } catch (RuntimeException ex) {
            fileSystemRepository.deleteFile(storedName);
            throw ex;
        }
    }

    /**
     * Deletes the row, then, once that has committed, the file: a failed or rolled-back commit never leaves a row
     * without its file (an orphan file is the lesser harm, decisions.md 3b-21).
     */
    @Transactional
    public void removeMedia(Long id) {
        Media media = mediaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(String.format(MEDIA_NOT_FOUND, id)));
        mediaRepository.delete(media);
        mediaRepository.flush();
        String filePath = media.getFilePath();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    fileSystemRepository.deleteFile(filePath);
                } catch (RuntimeException ex) {
                    log.warn("Media {} deleted but its file {} could not be removed", id, filePath, ex);
                }
            }
        });
    }

    public MediaVm getMediaById(Long id) {
        return mediaRepository.findById(id)
            .map(this::toVm)
            .orElseThrow(() -> new NotFoundException(String.format(MEDIA_NOT_FOUND, id)));
    }

    public List<MediaVm> getMediaByIds(List<Long> ids) {
        return mediaRepository.findAllById(ids).stream().map(this::toVm).toList();
    }

    /**
     * The stored file of a media whose display name is exactly {@code fileName}; 404 otherwise.
     */
    public MediaFile getFile(Long id, String fileName) {
        Media media = mediaRepository.findById(id)
            .filter(found -> found.getFileName() != null && found.getFileName().equals(fileName))
            .orElseThrow(() -> new NotFoundException(String.format(MEDIA_NOT_FOUND, id)));
        Path path = fileSystemRepository.getFile(media.getFilePath());
        if (path == null) {
            throw new NotFoundException(String.format(MEDIA_NOT_FOUND, id));
        }
        return new MediaFile(path, MediaType.parseMediaType(media.getMediaType()), media.getFileName());
    }

    private MediaVm toVm(Media media) {
        String url = UriComponentsBuilder.fromUriString(properties.publicUrl())
            .path("/medias/{id}/file/{fileName}")
            .buildAndExpand(media.getId(), media.getFileName())
            .encode()
            .toUriString();
        return new MediaVm(media.getId(), media.getCaption(), media.getFileName(), media.getMediaType(), url);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** A stored file to send: its path, content type and display name. */
    public record MediaFile(Path path, MediaType mediaType, String fileName) {
    }
}
