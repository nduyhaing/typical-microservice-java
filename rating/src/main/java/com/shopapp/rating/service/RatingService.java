package com.shopapp.rating.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.ForbiddenException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.exception.ResourceExistedException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.rating.model.Rating;
import com.shopapp.rating.repository.RatingRepository;
import com.shopapp.rating.utils.Constants;
import com.shopapp.rating.viewmodel.RatingListVm;
import com.shopapp.rating.viewmodel.RatingPostVm;
import com.shopapp.rating.viewmodel.RatingVm;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ratings (YAS RatingService). */
@Service
@RequiredArgsConstructor
public class RatingService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdOn"), Sort.Order.desc("id"));
    private static final ZonedDateTime EARLIEST = Instant.EPOCH.atZone(ZoneOffset.UTC);
    private static final ZonedDateTime LATEST = ZonedDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

    private final RatingRepository ratingRepository;
    private final OrderService orderService;
    private final ProductService productService;

    @Transactional(readOnly = true)
    public RatingListVm getRatingListByProductId(Long productId, int pageNo, int pageSize) {
        return toListVm(ratingRepository.findByProductId(productId, PageRequest.of(pageNo, pageSize, NEWEST_FIRST)));
    }

    @Transactional(readOnly = true)
    public RatingListVm getRatingListWithFilter(String productName, String customerName, String message,
                                                ZonedDateTime createdFrom, ZonedDateTime createdTo,
                                                int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize, NEWEST_FIRST);
        return toListVm(ratingRepository.getRatingListWithFilter(likeTerm(productName), likeTerm(customerName),
            likeTerm(message), createdFrom == null ? EARLIEST : createdFrom, createdTo == null ? LATEST : createdTo,
            pageable));
    }

    @Transactional(readOnly = true)
    public List<RatingVm> getLatestRatings(int count) {
        return ratingRepository.getLatestRatings(PageRequest.of(0, count)).stream().map(RatingVm::fromModel).toList();
    }

    /** Average star of the product, 0 without ratings (YAS). */
    @Transactional(readOnly = true)
    public Double calculateAverageStar(Long productId) {
        Double average = ratingRepository.getAverageStar(productId);
        return average == null ? 0.0 : average;
    }

    /**
     * A customer's rating. Only a customer who bought the product (order, with the customer's token) may rate it,
     * once per product (decisions.md 4-32); the product name comes from product and the customer's name from the
     * token (4-31). The remote calls run before anything is written, so a failure saves nothing.
     */
    public RatingVm createRating(RatingPostVm ratingPostVm) {
        String userId = AuthenticationUtils.extractUserId();
        Long productId = ratingPostVm.productId();
        if (ratingRepository.existsByCreatedByAndProductId(userId, productId)) {
            throw new ResourceExistedException(Constants.ErrorCode.RATING_ALREADY_EXISTED);
        }
        if (!orderService.hasBought(productId)) {
            throw new BadRequestException(Constants.ErrorCode.RATING_NOT_ALLOWED);
        }
        String productName = productService.getProductName(productId);

        Rating rating = new Rating();
        rating.setRatingStar(ratingPostVm.star());
        rating.setContent(ratingPostVm.content().strip());
        rating.setProductId(productId);
        rating.setProductName(productName);
        Jwt jwt = currentJwt();
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");
        if (isBlank(firstName) && isBlank(lastName)) {
            firstName = jwt.getClaimAsString("preferred_username");
        }
        rating.setFirstName(firstName == null ? "" : firstName);
        rating.setLastName(lastName == null ? "" : lastName);
        try {
            return RatingVm.fromModel(ratingRepository.saveAndFlush(rating));
        } catch (DataIntegrityViolationException ex) {
            // a concurrent second rating of the same product hit the unique (created_by, product_id) constraint
            throw new ResourceExistedException(Constants.ErrorCode.RATING_ALREADY_EXISTED);
        }
    }

    /** ADMIN: deletes any rating. */
    @Transactional
    public void deleteRating(Long ratingId) {
        ratingRepository.delete(findRating(ratingId));
    }

    /** CUSTOMER: deletes the caller's own rating; someone else's is 403 (decisions.md 4-33). */
    @Transactional
    public void deleteOwnRating(Long ratingId) {
        Rating rating = findRating(ratingId);
        if (!AuthenticationUtils.extractUserId().equals(rating.getCreatedBy())) {
            throw new ForbiddenException(Constants.ErrorCode.RATING_NOT_YOURS);
        }
        ratingRepository.delete(rating);
    }

    private Rating findRating(Long ratingId) {
        return ratingRepository.findById(ratingId)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.RATING_NOT_FOUND, ratingId));
    }

    private static RatingListVm toListVm(Page<Rating> ratings) {
        return new RatingListVm(ratings.getContent().stream().map(RatingVm::fromModel).toList(),
            ratings.getTotalElements(), ratings.getTotalPages());
    }

    /** Lower-cased, with LIKE wildcards and the escape character escaped (ESCAPE '\'). */
    private static String likeTerm(String value) {
        if (value == null) {
            return "";
        }
        return value.strip().toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Jwt currentJwt() {
        return ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
    }
}
