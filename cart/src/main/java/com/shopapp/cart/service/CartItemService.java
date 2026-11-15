package com.shopapp.cart.service;

import com.shopapp.cart.mapper.CartItemMapper;
import com.shopapp.cart.model.CartItem;
import com.shopapp.cart.repository.CartItemRepository;
import com.shopapp.cart.utils.Constants;
import com.shopapp.cart.viewmodel.CartItemDeleteVm;
import com.shopapp.cart.viewmodel.CartItemGetVm;
import com.shopapp.cart.viewmodel.CartItemPostVm;
import com.shopapp.cart.viewmodel.CartItemPutVm;
import com.shopapp.cart.viewmodel.ProductThumbnailVm;
import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartItemService {
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;
    private final CartItemMapper cartItemMapper;

    @Transactional
    public CartItemGetVm addCartItem(CartItemPostVm cartItemPostVm) {
        ProductThumbnailVm product = validateProduct(cartItemPostVm.productId());

        String currentUserId = AuthenticationUtils.extractUserId();
        CartItem cartItem = performAddCartItem(cartItemPostVm, currentUserId, product);

        return cartItemMapper.toGetVm(cartItem);
    }

    @Transactional
    public CartItemGetVm updateCartItem(Long productId, CartItemPutVm cartItemPutVm) {
        ProductThumbnailVm product = validateProduct(productId);
        requireStock(product, cartItemPutVm.quantity());

        String currentUserId = AuthenticationUtils.extractUserId();
        CartItem cartItem = cartItemMapper.toCartItem(currentUserId, productId, cartItemPutVm.quantity());

        CartItem savedCartItem = cartItemRepository.save(cartItem);
        return cartItemMapper.toGetVm(savedCartItem);
    }

    public List<CartItemGetVm> getCartItems() {
        String currentUserId = AuthenticationUtils.extractUserId();
        List<CartItem> cartItems = cartItemRepository.findByCustomerIdOrderByCreatedOnDesc(currentUserId);
        return cartItemMapper.toGetVms(cartItems);
    }

    @Transactional
    public List<CartItemGetVm> deleteOrAdjustCartItem(List<CartItemDeleteVm> cartItemDeleteVms) {
        validateCartItemDeleteVms(cartItemDeleteVms);

        Map<Long, CartItem> cartItemById = getCartItemsByProductIds(cartItemDeleteVms);

        List<CartItem> cartItemsToDelete = new ArrayList<>();
        List<CartItem> cartItemsToAdjust = new ArrayList<>();

        for (CartItemDeleteVm cartItemDeleteVm : cartItemDeleteVms) {
            Optional<CartItem> optionalCartItem = Optional.ofNullable(cartItemById.get(cartItemDeleteVm.productId()));
            optionalCartItem.ifPresent(cartItem -> {
                if (cartItem.getQuantity() <= cartItemDeleteVm.quantity()) {
                    cartItemsToDelete.add(cartItem);
                } else {
                    cartItem.setQuantity(cartItem.getQuantity() - cartItemDeleteVm.quantity());
                    cartItemsToAdjust.add(cartItem);
                }
            });
        }

        cartItemRepository.deleteAll(cartItemsToDelete);
        List<CartItem> updatedCartItems = cartItemRepository.saveAll(cartItemsToAdjust);

        return cartItemMapper.toGetVms(updatedCartItems);
    }

    @Transactional
    public void deleteCartItem(Long productId) {
        String currentUserId = AuthenticationUtils.extractUserId();
        cartItemRepository.deleteByCustomerIdAndProductId(currentUserId, productId);
    }

    private ProductThumbnailVm validateProduct(Long productId) {
        return productService.getProduct(productId)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.NOT_FOUND_PRODUCT, productId));
    }

    /**
     * A stock-tracked product can only be in a cart up to its stock (decisions.md 3c-47); untracked products are not
     * limited. The order still takes the stock atomically, so this is a convenience check, not the guarantee.
     */
    private static void requireStock(ProductThumbnailVm product, long quantity) {
        Long available = product.availableQuantity();
        if (available == null || quantity <= available) {
            return;
        }
        if (available == 0) {
            throw new BadRequestException(Constants.ErrorCode.OUT_OF_STOCK, product.name());
        }
        throw new BadRequestException(Constants.ErrorCode.NOT_ENOUGH_STOCK, available, product.name());
    }

    private CartItem performAddCartItem(CartItemPostVm cartItemPostVm, String currentUserId,
                                        ProductThumbnailVm product) {
        try {
            return cartItemRepository.findByCustomerIdAndProductId(currentUserId, cartItemPostVm.productId())
                .map(existingCartItem -> updateExistingCartItem(cartItemPostVm, existingCartItem, product))
                .orElseGet(() -> {
                    requireStock(product, cartItemPostVm.quantity());
                    return createNewCartItem(cartItemPostVm, currentUserId);
                });
        } catch (PessimisticLockingFailureException e) {
            log.error("Failed to acquire lock for adding cart item", e);
            throw new InternalServerErrorException(Constants.ErrorCode.ADD_CART_ITEM_FAILED);
        }
    }

    private CartItem createNewCartItem(CartItemPostVm cartItemPostVm, String currentUserId) {
        CartItem cartItem = cartItemMapper.toCartItem(cartItemPostVm, currentUserId);
        return cartItemRepository.save(cartItem);
    }

    private CartItem updateExistingCartItem(CartItemPostVm cartItemPostVm, CartItem existingCartItem,
                                            ProductThumbnailVm product) {
        // checked in long arithmetic: YAS overflowed int here and stored a negative quantity
        long merged = (long) existingCartItem.getQuantity() + cartItemPostVm.quantity();
        if (merged > Constants.MAX_CART_QUANTITY) {
            throw new BadRequestException(Constants.ErrorCode.QUANTITY_TOO_LARGE, Constants.MAX_CART_QUANTITY);
        }
        requireStock(product, merged);
        existingCartItem.setQuantity((int) merged);
        return cartItemRepository.save(existingCartItem);
    }

    private void validateCartItemDeleteVms(List<CartItemDeleteVm> cartItemDeleteVms) {
        Map<Long, Integer> quantityByProductId = new HashMap<>();

        for (CartItemDeleteVm cartItemDeleteVm : cartItemDeleteVms) {
            Integer existingQuantity = quantityByProductId.get(cartItemDeleteVm.productId());

            if (!Objects.isNull(existingQuantity) && !existingQuantity.equals(cartItemDeleteVm.quantity())) {
                throw new BadRequestException(Constants.ErrorCode.DUPLICATED_CART_ITEMS_TO_DELETE);
            }

            quantityByProductId.put(cartItemDeleteVm.productId(), cartItemDeleteVm.quantity());
        }
    }

    private Map<Long, CartItem> getCartItemsByProductIds(List<CartItemDeleteVm> cartItemDeleteVms) {
        String currentUserId = AuthenticationUtils.extractUserId();
        List<Long> productIds = cartItemDeleteVms
            .stream()
            .map(CartItemDeleteVm::productId)
            .toList();
        List<CartItem> cartItems = cartItemRepository.findByCustomerIdAndProductIdIn(currentUserId, productIds);
        return cartItems
            .stream()
            .collect(Collectors.toMap(CartItem::getProductId, Function.identity()));
    }
}