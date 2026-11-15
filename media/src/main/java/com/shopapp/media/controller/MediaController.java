package com.shopapp.media.controller;

import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import com.shopapp.media.service.MediaService;
import com.shopapp.media.service.MediaService.MediaFile;
import com.shopapp.media.viewmodel.MediaPostVm;
import com.shopapp.media.viewmodel.MediaVm;
import com.shopapp.media.viewmodel.NoFileMediaVm;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
public class MediaController {

    static final int MAX_IDS = 100;

    private final MediaService mediaService;

    @PostMapping(path = "/medias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ok",
            content = @Content(schema = @Schema(implementation = NoFileMediaVm.class))),
        @ApiResponse(responseCode = "400", description = "Bad request",
            content = @Content(schema = @Schema(implementation = ErrorVm.class)))})
    public ResponseEntity<NoFileMediaVm> create(@ModelAttribute @Valid MediaPostVm mediaPostVm) {
        return ResponseEntity.ok(mediaService.saveMedia(mediaPostVm));
    }

    @DeleteMapping("/medias/{id}")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorVm.class)))})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        mediaService.removeMedia(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/medias/{id}")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Ok",
            content = @Content(schema = @Schema(implementation = MediaVm.class))),
        @ApiResponse(responseCode = "404", description = "Not found",
            content = @Content(schema = @Schema(implementation = ErrorVm.class)))})
    public ResponseEntity<MediaVm> get(@PathVariable Long id) {
        return ResponseEntity.ok(mediaService.getMediaById(id));
    }

    /**
     * The media with the given ids (unknown ids are skipped; an empty list when none exists).
     */
    @GetMapping("/medias")
    public ResponseEntity<List<MediaVm>> getByIds(@RequestParam @NotEmpty @Size(max = MAX_IDS) List<Long> ids) {
        return ResponseEntity.ok(mediaService.getMediaByIds(ids));
    }

    /**
     * The image bytes with their stored content type; cacheable by browsers and proxies for a day.
     */
    @GetMapping("/medias/{id}/file/{fileName}")
    public ResponseEntity<Resource> getFile(@PathVariable Long id, @PathVariable String fileName) {
        MediaFile file = mediaService.getFile(id, fileName);
        return ResponseEntity.ok()
            .contentType(file.mediaType())
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(file.fileName()).build().toString())
            .body(new FileSystemResource(file.path()));
    }
}
