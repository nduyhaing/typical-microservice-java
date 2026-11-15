package com.shopapp.order.repository;

import com.shopapp.order.model.Order;
import com.shopapp.order.model.enumeration.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findAllByCustomerIdOrderByCreatedOnDescIdDesc(String customerId);

    Optional<Order> findByCheckoutId(String checkoutId);

    boolean existsByCheckoutId(String checkoutId);

    /** Whether the customer has an order in one of {@code statuses} with a line for one of the products (4-30). */
    @Query("SELECT COUNT(o) > 0 FROM Order o WHERE o.customerId = :customerId AND o.orderStatus IN :statuses"
        + " AND EXISTS (SELECT 1 FROM OrderItem i WHERE i.orderId = o.id AND i.productId IN :productIds)")
    boolean existsBoughtProduct(@Param("customerId") String customerId,
                                @Param("productIds") Collection<Long> productIds,
                                @Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT o FROM Order o ORDER BY o.createdOn DESC")
    List<Order> getLatestOrders(Pageable pageable);
}
