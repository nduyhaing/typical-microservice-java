package com.shopapp.product.viewmodel.category;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Back office category create/update body (YAS shape). */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record CategoryPostVm(@NotBlank @Size(max = 255) String name, @NotBlank @Size(max = 255) String slug,
                             @Size(max = 255) String description, Long parentId,
                             @Size(max = 255) String metaKeywords, @Size(max = 255) String metaDescription,
                             Short displayOrder, Boolean isPublish, Long imageId) {
}
