package com.shopapp.order.repository;

import com.shopapp.order.model.Order;
import com.shopapp.order.model.enumeration.OrderStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    List<Order> findAllByCustomerIdOrderByCreatedOnDescIdDesc(String customerId);

    /** The back-office list (5-1) with each order's billing address in the same query. */
    @Override
    @EntityGraph(attributePaths = "billingAddressId")
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

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

    /** Locks the order row so status changes (admin, customer cancel, payment) run one after another (5-1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findWithLockById(@Param("id") Long id);

    /** Records that product gave the order's stock back; 0 when it was already recorded. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE \"order\" SET stock_restored_on = now() WHERE id = :id AND stock_restored_on IS NULL",
        nativeQuery = true)
    int markStockRestored(@Param("id") Long id);

    /** Records that promotion gave the order's coupon use back; 0 when it was already recorded. */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE \"order\" SET promotion_usage_released_on = now() WHERE id = :id"
        + " AND promotion_usage_released_on IS NULL", nativeQuery = true)
    int markCouponReleased(@Param("id") Long id);

    /** Orders asked to give their stock or coupon use back that still owe one of them (compensation sweep, 5-4). */
    @Query(value = "SELECT id FROM \"order\" WHERE goods_back_requested_on IS NOT NULL"
        + " AND ((stock_reference IS NOT NULL AND stock_restored_on IS NULL)"
        + " OR (promotion_usage_reference IS NOT NULL AND promotion_usage_released_on IS NULL))"
        + " ORDER BY id LIMIT :limit", nativeQuery = true)
    List<Long> findIdsOwingGoodsBack(@Param("limit") int limit);
}
