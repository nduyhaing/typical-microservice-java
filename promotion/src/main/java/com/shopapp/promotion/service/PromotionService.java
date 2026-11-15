package com.shopapp.promotion.service;

import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.promotion.exception.PromotionException;
import com.shopapp.promotion.model.Promotion;
import com.shopapp.promotion.model.PromotionApply;
import com.shopapp.promotion.model.PromotionUsage;
import com.shopapp.promotion.model.enumeration.ApplyTo;
import com.shopapp.promotion.model.enumeration.DiscountType;
import com.shopapp.promotion.model.enumeration.UsageType;
import com.shopapp.promotion.repository.PromotionRepository;
import com.shopapp.promotion.repository.PromotionUsageRepository;
import com.shopapp.promotion.utils.Constants.ErrorCode;
import com.shopapp.promotion.viewmodel.PromotionApplyUsageVm;
import com.shopapp.promotion.viewmodel.PromotionDetailVm;
import com.shopapp.promotion.viewmodel.PromotionInput;
import com.shopapp.promotion.viewmodel.PromotionListVm;
import com.shopapp.promotion.viewmodel.PromotionPostVm;
import com.shopapp.promotion.viewmodel.PromotionPutVm;
import com.shopapp.promotion.viewmodel.PromotionUsageResultVm;
import com.shopapp.promotion.viewmodel.PromotionVerifyResultDto;
import com.shopapp.promotion.viewmodel.PromotionVerifyVm;
import com.shopapp.promotion.viewmodel.product.ProductCheckoutVm;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final ProductService productService;

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public PromotionListVm getPromotions(int pageNo, int pageSize, String promotionName, String couponCode,
                                         Instant startDate, Instant endDate) {
        Page<Promotion> page = promotionRepository.findPromotions(likeValue(promotionName), likeValue(couponCode),
            startDate, endDate, PageRequest.of(pageNo, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        return new PromotionListVm(page.getContent().stream().map(PromotionDetailVm::fromModel).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PromotionDetailVm getPromotion(Long id) {
        return PromotionDetailVm.fromModel(findPromotion(id));
    }

    @Transactional
    public PromotionDetailVm createPromotion(PromotionPostVm vm) {
        Promotion promotion = new Promotion();
        apply(promotion, vm, null);
        return PromotionDetailVm.fromModel(promotionRepository.saveAndFlush(promotion));
    }

    /** All editable fields are replaced; the usage count is kept and a limited limit may not go below it. */
    @Transactional
    public PromotionDetailVm updatePromotion(PromotionPutVm vm) {
        Promotion promotion = findPromotion(vm.id());
        if (vm.usageType() == UsageType.LIMITED && vm.usageLimit() < promotion.getUsageCount()) {
            throw new PromotionException(ErrorCode.USAGE_LIMIT_BELOW_USAGE_COUNT, promotion.getUsageCount());
        }
        apply(promotion, vm, promotion.getId());
        return PromotionDetailVm.fromModel(promotionRepository.saveAndFlush(promotion));
    }

    /** A promotion that has been used (even if released) keeps its history: 400, deactivate it instead (YAS). */
    @Transactional
    public void deletePromotion(Long id) {
        Promotion promotion = findPromotion(id);
        if (promotionUsageRepository.existsByPromotionId(id)) {
            throw new PromotionException(ErrorCode.PROMOTION_IN_USE, id);
        }
        promotionRepository.delete(promotion);
    }

    // ---------------------------------------------------------------- customer

    /**
     * Checks the coupon for an order (any authenticated user, no transaction held during the product calls): it
     * exists and is active, now is within its dates, it is not used up, the order price reaches the minimum, and it
     * applies to at least one of the products; each failure is a 400 with its own code.
     */
    public PromotionVerifyResultDto verifyPromotion(PromotionVerifyVm vm) {
        Instant now = Instant.now();
        Promotion promotion = findUsable(vm.couponCode(), now);
        if (isUsedUp(promotion)) {
            throw new PromotionException(ErrorCode.PROMOTION_USED_UP, promotion.getCouponCode());
        }
        if (vm.orderPrice().compareTo(promotion.getMinimumOrderPurchaseAmount()) < 0) {
            throw new PromotionException(ErrorCode.MINIMUM_ORDER_NOT_REACHED, promotion.getCouponCode(),
                promotion.getMinimumOrderPurchaseAmount().toPlainString());
        }
        List<ProductCheckoutVm> eligible = eligibleProducts(promotion, vm.productIds().stream().distinct().toList());
        if (eligible.isEmpty()) {
            throw new PromotionException(ErrorCode.PROMOTION_NOT_APPLICABLE, promotion.getCouponCode());
        }
        Long cheapest = eligible.stream()
            .min(Comparator.comparing(ProductCheckoutVm::price, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(ProductCheckoutVm::id)
            .orElseThrow();
        BigDecimal discountValue = promotion.getDiscountType() == DiscountType.FIXED
            ? promotion.getDiscountAmount() : promotion.getDiscountPercentage();
        return new PromotionVerifyResultDto(true, cheapest, eligible.stream().map(ProductCheckoutVm::id).toList(),
            promotion.getCouponCode(), promotion.getDiscountType(), discountValue, promotion.getId(),
            promotion.getName());
    }

    /**
     * Takes one use of the coupon for the caller under {@code reference} (the order's idempotency key): the count is
     * incremented in one conditional UPDATE (active, within dates, below the limit), so the limit is never exceeded;
     * repeating the same reference returns the same use without counting it again.
     */
    @Transactional
    public PromotionUsageResultVm applyUsage(UUID reference, PromotionApplyUsageVm vm) {
        String userId = AuthenticationUtils.extractUserId();
        var existing = promotionUsageRepository.findByReference(reference);
        if (existing.isPresent()) {
            PromotionUsage usage = existing.get();
            Promotion promotion = promotionRepository.findById(usage.getPromotionId()).orElseThrow();
            if (!usage.getUserId().equals(userId) || usage.getReleasedOn() != null
                || !promotion.getCouponCode().equalsIgnoreCase(vm.couponCode().trim())) {
                throw new PromotionException(ErrorCode.USAGE_REFERENCE_CONFLICT, reference);
            }
            return new PromotionUsageResultVm(promotion.getId(), promotion.getCouponCode(), reference);
        }
        Instant now = Instant.now();
        Promotion promotion = promotionRepository.findByCouponCodeIgnoreCase(vm.couponCode().trim())
            .orElseThrow(() -> new PromotionException(ErrorCode.PROMOTION_NOT_FOUND, vm.couponCode()));
        Long promotionId = promotion.getId();
        String couponCode = promotion.getCouponCode();
        if (promotionRepository.incrementUsage(promotionId, now) == 0) {
            checkUsable(promotion, vm.couponCode(), now);
            throw new PromotionException(ErrorCode.PROMOTION_USED_UP, couponCode);
        }
        PromotionUsage usage = new PromotionUsage();
        usage.setPromotionId(promotionId);
        usage.setReference(reference);
        usage.setUserId(userId);
        usage.setOrderId(vm.orderId());
        usage.setProductId(vm.productIds() == null || vm.productIds().isEmpty() ? null : vm.productIds().getFirst());
        promotionUsageRepository.saveAndFlush(usage);
        return new PromotionUsageResultVm(promotionId, couponCode, reference);
    }

    /** Gives back the caller's use taken under {@code reference}, once; unknown or other users' references: no-op. */
    @Transactional
    public void releaseUsage(UUID reference) {
        String userId = AuthenticationUtils.extractUserId();
        promotionUsageRepository.findByReference(reference)
            .filter(usage -> promotionUsageRepository.release(reference, userId) == 1)
            .ifPresent(usage -> promotionRepository.decrementUsage(usage.getPromotionId()));
    }

    // ---------------------------------------------------------------- helpers

    private Promotion findUsable(String couponCode, Instant now) {
        Promotion promotion = promotionRepository.findByCouponCodeIgnoreCase(couponCode.trim())
            .orElseThrow(() -> new PromotionException(ErrorCode.PROMOTION_NOT_FOUND, couponCode));
        checkUsable(promotion, couponCode, now);
        return promotion;
    }

    /** An inactive promotion is reported as not found (YAS looked up active promotions only). */
    private static void checkUsable(Promotion promotion, String couponCode, Instant now) {
        if (!promotion.isActive()) {
            throw new PromotionException(ErrorCode.PROMOTION_NOT_FOUND, couponCode);
        }
        if (now.isBefore(promotion.getStartDate())) {
            throw new PromotionException(ErrorCode.PROMOTION_NOT_STARTED, promotion.getCouponCode());
        }
        if (now.isAfter(promotion.getEndDate())) {
            throw new PromotionException(ErrorCode.PROMOTION_EXPIRED, promotion.getCouponCode());
        }
    }

    private static boolean isUsedUp(Promotion promotion) {
        return promotion.getUsageType() == UsageType.LIMITED
            && promotion.getUsageCount() >= promotion.getUsageLimit();
    }

    /**
     * The requested products (orderable ones, in request order) the promotion applies to. A variation counts through
     * its parent: PRODUCT matches the product or its parent; BRAND matches the product's brand, or for a variation
     * without a brand, its parent being a product of the brand; CATEGORY matches the product or its parent being
     * directly in one of the categories.
     */
    private List<ProductCheckoutVm> eligibleProducts(Promotion promotion, List<Long> productIds) {
        List<ProductCheckoutVm> products = productService.getOrderableProducts(productIds);
        Set<Long> targets = Set.copyOf(PromotionInputs.targetIds(promotion));
        Predicate<ProductCheckoutVm> eligible = switch (promotion.getApplyTo()) {
            case PRODUCT -> product -> targets.contains(product.id()) || contains(targets, product.parentId());
            case BRAND -> {
                boolean needParents = products.stream()
                    .anyMatch(product -> product.brandId() == null && product.parentId() != null);
                Set<Long> brandProducts = needParents ? productService.getProductIdsOfBrands(targets) : Set.of();
                yield product -> contains(targets, product.brandId())
                    || product.brandId() == null && contains(brandProducts, product.parentId());
            }
            case CATEGORY -> {
                Set<Long> categoryProducts = productService.getProductIdsOfCategories(targets);
                yield product -> categoryProducts.contains(product.id())
                    || contains(categoryProducts, product.parentId());
            }
        };
        List<ProductCheckoutVm> matching = products.stream().filter(eligible).toList();
        return productIds.stream()
            .flatMap(id -> matching.stream().filter(product -> id.equals(product.id())).limit(1))
            .toList();
    }

    private static boolean contains(Set<Long> ids, Long id) {
        return id != null && ids.contains(id);
    }

    private void apply(Promotion promotion, PromotionInput vm, Long excludeId) {
        String couponCode = vm.couponCode().trim();
        String slug = vm.slug().trim();
        if (promotionRepository.existsCouponCode(couponCode, excludeId)) {
            throw new PromotionException(ErrorCode.COUPON_CODE_ALREADY_EXISTED, couponCode);
        }
        if (promotionRepository.existsSlug(slug, excludeId)) {
            throw new PromotionException(ErrorCode.SLUG_ALREADY_EXISTED, slug);
        }
        boolean percentage = vm.discountType() == DiscountType.PERCENTAGE;
        promotion.setName(vm.name().trim());
        promotion.setSlug(slug);
        promotion.setDescription(vm.description() == null || vm.description().isBlank()
            ? null : vm.description().trim());
        promotion.setCouponCode(couponCode);
        promotion.setDiscountType(vm.discountType());
        promotion.setDiscountPercentage(percentage ? vm.discountPercentage() : null);
        promotion.setDiscountAmount(percentage ? null : vm.discountAmount());
        promotion.setUsageType(vm.usageType());
        promotion.setUsageLimit(vm.usageType() == UsageType.LIMITED ? vm.usageLimit() : null);
        promotion.setMinimumOrderPurchaseAmount(Objects.requireNonNullElse(vm.minimumOrderPurchaseAmount(),
            BigDecimal.ZERO));
        promotion.setActive(vm.isActive());
        promotion.setStartDate(vm.startDate());
        promotion.setEndDate(vm.endDate());
        promotion.setApplyTo(vm.applyTo());
        promotion.replacePromotionApplies(vm.targetIds().stream().distinct()
            .map(id -> PromotionInputs.newApply(promotion, vm.applyTo(), id))
            .toList());
    }

    private Promotion findPromotion(Long id) {
        return promotionRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.PROMOTION_NOT_FOUND, id));
    }

    /** Lower-cased and LIKE-escaped with '!' (see PromotionRepository#findPromotions). */
    private static String likeValue(String filter) {
        return filter.trim().toLowerCase(Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** Mapping between promotion targets and PromotionApply rows. */
    static final class PromotionInputs {

        private PromotionInputs() {
        }

        static List<Long> targetIds(Promotion promotion) {
            return promotion.getPromotionApplies().stream()
                .map(apply -> switch (promotion.getApplyTo()) {
                    case PRODUCT -> apply.getProductId();
                    case BRAND -> apply.getBrandId();
                    case CATEGORY -> apply.getCategoryId();
                })
                .filter(Objects::nonNull)
                .toList();
        }

        static PromotionApply newApply(Promotion promotion, ApplyTo applyTo, Long id) {
            PromotionApply apply = new PromotionApply();
            apply.setPromotion(promotion);
            switch (applyTo) {
                case PRODUCT -> apply.setProductId(id);
                case BRAND -> apply.setBrandId(id);
                default -> apply.setCategoryId(id);
            }
            return apply;
        }
    }
}
