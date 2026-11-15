package com.shopapp.media.viewmodel;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload form (multipart). The file's type is checked by decoding it in the service, not by a field validator.
 */
public record MediaPostVm(
    @Size(max = 255) String caption,
    @NotNull MultipartFile multipartFile,
    @Size(max = 255) String fileNameOverride
) {
}
