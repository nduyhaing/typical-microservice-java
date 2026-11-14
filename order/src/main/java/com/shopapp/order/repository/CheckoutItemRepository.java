package com.shopapp.order.repository;

import com.shopapp.order.model.CheckoutItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckoutItemRepository extends JpaRepository<CheckoutItem, Long> {

    List<CheckoutItem> findAllByCheckoutId(String checkoutId);
}
