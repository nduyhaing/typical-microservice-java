package com.shopapp.location.viewmodel.stateorprovince;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * State/province create/update body (YAS shape). {@code countryId} is required on create and ignored on update
 * (YAS: a state never moves to another country).
 */
public record StateOrProvincePostVm(@NotBlank @Size(max = 450) String name,
                                    @Size(max = 255) String code,
                                    @Size(max = 255) String type,
                                    Long countryId) {
}
