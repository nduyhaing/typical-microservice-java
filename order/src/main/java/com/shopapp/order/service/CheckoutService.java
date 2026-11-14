package com.shopapp.order.service;

import com.shopapp.commonlibrary.exception.ForbiddenException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.order.model.Checkout;
import com.shopapp.order.model.CheckoutItem;
import com.shopapp.order.model.enumeration.CheckoutState;
import com.shopapp.order.repository.CheckoutRepository;
import com.shopapp.order.utils.Constants;
import com.shopapp.order.viewmodel.checkout.CheckoutItemPostVm;
import com.shopapp.order.viewmodel.checkout.CheckoutPostVm;
import com.shopapp.order.viewmodel.checkout.CheckoutVm;
import com.shopapp.order.viewmodel.external.ProductCheckoutListVm;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkouts (ported from YAS CheckoutService): a priced snapshot of what the customer wants to order.
 * Prices always come from the product service, never from the browser (decisions.md 2c-1).
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CheckoutRepository checkoutRepository;
    private final ProductService productService;

    @Transactional
    public CheckoutVm createCheckout(CheckoutPostVm checkoutPostVm) {
        String customerId = AuthenticationUtils.extractUserId();
        List<Long> productIds = checkoutPostVm.checkoutItemPostVms().stream()
            .map(CheckoutItemPostVm::productId).distinct().toList();
        Map<Long, ProductCheckoutListVm> products = productService.getOrderableProducts(productIds);

        Checkout checkout = Checkout.builder()
            .customerId(customerId)
            .email(CurrentUser.email())
            .note(checkoutPostVm.note())
            .checkoutState(CheckoutState.PENDING)
            .build();
        for (CheckoutItemPostVm requested : checkoutPostVm.checkoutItemPostVms()) {
            ProductCheckoutListVm product = products.get(requested.productId());
            if (product == null || product.price() == null) {
                throw new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, requested.productId());
            }
            checkout.getCheckoutItems().add(CheckoutItem.builder()
                .checkout(checkout)
                .productId(product.id())
                .productName(product.name())
                .quantity(requested.quantity())
                .productPrice(BigDecimal.valueOf(product.price()))
                .build());
        }
        checkout.setTotalAmount(checkout.getCheckoutItems().stream()
            .map(item -> item.getProductPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        return CheckoutVm.fromModel(checkoutRepository.save(checkout));
    }

    @Transactional(readOnly = true)
    public CheckoutVm getPendingCheckout(String id) {
        Checkout checkout = checkoutRepository.findByIdAndCheckoutState(id, CheckoutState.PENDING)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.CHECKOUT_NOT_FOUND, id));
        requireOwner(checkout);
        return CheckoutVm.fromModel(checkout);
    }

    static void requireOwner(Checkout checkout) {
        if (!AuthenticationUtils.extractUserId().equals(checkout.getCustomerId())) {
            throw new ForbiddenException(Constants.ErrorCode.CHECKOUT_NOT_YOURS);
        }
    }
}
