package com.shopapp.product.viewmodel.brand;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.shopapp.product.model.Brand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Back office brand create/update body (YAS shape). */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record BrandPostVm(@NotBlank @Size(max = 255) String name, @NotBlank @Size(max = 255) String slug,
                          boolean isPublish) {

    public Brand toModel() {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setSlug(slug);
        brand.setPublished(isPublish);
        return brand;
    }
}
