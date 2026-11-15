package com.shopapp.rating.viewmodel;

import com.shopapp.rating.model.Rating;
import java.time.ZonedDateTime;

public record RatingVm(Long id, String content, int star, Long productId, String productName, String createdBy,
                       String lastName, String firstName, ZonedDateTime createdOn) {

    public static RatingVm fromModel(Rating rating) {
        return new RatingVm(rating.getId(), rating.getContent(), rating.getRatingStar(), rating.getProductId(),
            rating.getProductName(), rating.getCreatedBy(), rating.getLastName(), rating.getFirstName(),
            rating.getCreatedOn());
    }
}
