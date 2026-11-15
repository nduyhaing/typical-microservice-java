package com.shopapp.promotion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.promotion.support.AbstractPromotionIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PromotionVerifyIT extends AbstractPromotionIT {

    private static final long SAMSUNG = 2;
    private static final long APPLE = 1;
    private static final long PHONES = 10;
    private static final long LAPTOPS = 11;

    @BeforeEach
    void catalog() {
        PRODUCT.brand(APPLE, "apple");
        PRODUCT.brand(SAMSUNG, "samsung");
        PRODUCT.category(PHONES, "phones");
        PRODUCT.category(LAPTOPS, "laptops");
        PRODUCT.product(1, null, APPLE, 999.0, PHONES);         // iPhone
        PRODUCT.product(4, null, SAMSUNG, 499.0, PHONES);       // Galaxy A55
        PRODUCT.product(6, null, APPLE, 1299.0, LAPTOPS);       // MacBook
        PRODUCT.product(24, 20L, null, 899.0);                  // Galaxy S24 Black: variation without brand
        PRODUCT.product(20, null, SAMSUNG, 899.0, PHONES);      // Galaxy S24 (parent)
        PRODUCT.product(25, 20L, SAMSUNG, 799.0);               // Galaxy S24 Violet: variation with brand
    }

    private ResultActions verify(String code, String orderPrice, String productIds) throws Exception {
        return mockMvc.perform(post("/storefront/promotions/verify").with(customer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"couponCode\":\"" + code + "\",\"orderPrice\":" + orderPrice + ",\"productIds\":["
                + productIds + "]}"));
    }

    private void expectCode(ResultActions result, String code) throws Exception {
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.title").value(code));
    }

    private void brandPromotion(String code, long brandId) throws Exception {
        createPromotion(promotionJson(code).replace("\"applyTo\":\"PRODUCT\"", "\"applyTo\":\"BRAND\"")
            .replace("\"brandIds\":[]", "\"brandIds\":[" + brandId + "]"));
    }

    @Test
    void validCouponReturnsTheDiscountAndEligibleProducts() throws Exception {
        createPromotion(promotionJson("SAVE10").replace("\"productIds\":[1]", "\"productIds\":[1,6]"));

        verify("save10", "2298", "6,4,1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isValid").value(true))
            .andExpect(jsonPath("$.couponCode").value("SAVE10"))
            .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
            .andExpect(jsonPath("$.discountValue").value(10))
            .andExpect(jsonPath("$.productIds", contains(6, 1)))
            .andExpect(jsonPath("$.productId").value(1))
            .andExpect(jsonPath("$.promotionName").value("Promo SAVE10"));
        assertThat(PRODUCT.requests()).anyMatch(r -> r.startsWith("GET /product/products?ids=6,4,1")
            && r.endsWith("| Bearer " + CUSTOMER_TOKEN));
    }

    @Test
    void fixedDiscountValueIsTheAmount() throws Exception {
        createPromotion(promotionJson("FIVEOFF").replace("\"discountType\":\"PERCENTAGE\"", "\"discountType\":\"FIXED\"")
            .replace("\"discountAmount\":null", "\"discountAmount\":5.25"));
        verify("FIVEOFF", "10", "1").andExpect(status().isOk())
            .andExpect(jsonPath("$.discountType").value("FIXED"))
            .andExpect(jsonPath("$.discountValue").value(5.25));
    }

    @Test
    void unknownOrInactiveCouponIsNotFound() throws Exception {
        expectCode(verify("NOPE", "10", "1"), "PROMOTION_NOT_FOUND");
        createPromotion(promotionJson("OFF").replace("\"isActive\":true", "\"isActive\":false"));
        expectCode(verify("OFF", "10", "1"), "PROMOTION_NOT_FOUND");
    }

    @Test
    void couponOutsideItsDatesIsRefused() throws Exception {
        createPromotion(promotionJson("LATER", daysFromNow(1), daysFromNow(5)));
        createPromotion(promotionJson("OVER", daysFromNow(-5), daysFromNow(-1)));
        expectCode(verify("LATER", "10", "1"), "PROMOTION_NOT_STARTED");
        expectCode(verify("OVER", "10", "1"), "PROMOTION_EXPIRED");
    }

    @Test
    void usedUpCouponIsRefused() throws Exception {
        long id = createPromotion(promotionJson("ONCE")
            .replace("\"usageType\":\"UNLIMITED\",\"usageLimit\":null", "\"usageType\":\"LIMITED\",\"usageLimit\":1"));
        verify("ONCE", "10", "1").andExpect(status().isOk());
        jdbc.update("update promotion set usage_count = 1 where id = ?", id);
        expectCode(verify("ONCE", "10", "1"), "PROMOTION_USED_UP");
    }

    @Test
    void orderBelowTheMinimumIsRefused() throws Exception {
        createPromotion(promotionJson("BIG").replace("\"minimumOrderPurchaseAmount\":0",
            "\"minimumOrderPurchaseAmount\":100"));
        expectCode(verify("BIG", "99.99", "1"), "MINIMUM_ORDER_NOT_REACHED");
        verify("BIG", "100", "1").andExpect(status().isOk());
    }

    @Test
    void couponForOtherProductsIsNotApplicable() throws Exception {
        createPromotion(promotionJson("IPHONE"));
        expectCode(verify("IPHONE", "500", "4,6"), "PROMOTION_NOT_APPLICABLE");
        expectCode(verify("IPHONE", "500", "777"), "PROMOTION_NOT_APPLICABLE");
    }

    @Test
    void productPromotionCoversVariationsOfTheProduct() throws Exception {
        createPromotion(promotionJson("S24").replace("\"productIds\":[1]", "\"productIds\":[20]"));
        verify("S24", "899", "24,1").andExpect(status().isOk())
            .andExpect(jsonPath("$.productIds", contains(24)));
    }

    @Test
    void brandPromotionAppliesToTheBrandsProductsAndVariations() throws Exception {
        brandPromotion("SAMSUNG10", SAMSUNG);

        verify("SAMSUNG10", "3000", "1,4,24,25,6").andExpect(status().isOk())
            .andExpect(jsonPath("$.productIds", contains(4, 24, 25)))
            .andExpect(jsonPath("$.productId").value(4));
        expectCode(verify("SAMSUNG10", "3000", "1,6"), "PROMOTION_NOT_APPLICABLE");
    }

    @Test
    void brandPromotionDoesNotListBrandProductsWhenEveryProductHasABrand() throws Exception {
        brandPromotion("APPLE5", APPLE);
        verify("APPLE5", "1000", "1,4").andExpect(status().isOk())
            .andExpect(jsonPath("$.productIds", contains(1)));
        assertThat(PRODUCT.requests()).noneMatch(r -> r.contains("/storefront/"));
    }

    @Test
    void categoryPromotionAppliesToProductsInTheCategoryAcrossPages() throws Exception {
        for (long id = 100; id < 250; id++) {
            PRODUCT.product(id, null, APPLE, 10.0, LAPTOPS);
        }
        createPromotion(promotionJson("LAPTOPS").replace("\"applyTo\":\"PRODUCT\"", "\"applyTo\":\"CATEGORY\"")
            .replace("\"categoryIds\":[]", "\"categoryIds\":[" + LAPTOPS + "]"));
        createPromotion(promotionJson("PHONES").replace("\"applyTo\":\"PRODUCT\"", "\"applyTo\":\"CATEGORY\"")
            .replace("\"categoryIds\":[]", "\"categoryIds\":[" + PHONES + "]"));

        verify("LAPTOPS", "2000", "1,6,249").andExpect(status().isOk())
            .andExpect(jsonPath("$.productIds", contains(6, 249)));
        verify("PHONES", "2000", "6,24").andExpect(status().isOk())
            .andExpect(jsonPath("$.productIds", contains(24)));
        expectCode(verify("PHONES", "2000", "6"), "PROMOTION_NOT_APPLICABLE");
    }

    @Test
    void productServiceDownIsAServerError() throws Exception {
        brandPromotion("DOWN", SAMSUNG);
        PRODUCT.down();
        verify("DOWN", "10", "4").andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Product service is unavailable, please try again later"));
    }

    @Test
    void requestIsValidatedAndNeedsAToken() throws Exception {
        createPromotion(promotionJson("CHECK"));
        verify("CHECK", "0", "1").andExpect(status().isBadRequest());
        verify("CHECK", "10", "").andExpect(status().isBadRequest());
        verify("", "10", "1").andExpect(status().isBadRequest());
        mockMvc.perform(post("/storefront/promotions/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"couponCode\":\"CHECK\",\"orderPrice\":10,\"productIds\":[1]}"))
            .andExpect(status().isUnauthorized());
    }
}
