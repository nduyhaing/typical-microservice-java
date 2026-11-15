package com.shopapp.payment.repository;

import com.shopapp.payment.model.Payment;
import com.shopapp.payment.model.enumeration.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    /** The payment row locked for the rest of the transaction: one capture / confirmation at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findWithLockById(@Param("id") Long id);

    Optional<Payment> findByPaymentProviderCheckoutId(String paymentProviderCheckoutId);

    List<Payment> findByOrderIdAndPaymentStatus(Long orderId, PaymentStatus paymentStatus);

    boolean existsByOrderIdAndPaymentStatus(Long orderId, PaymentStatus paymentStatus);

    /**
     * Cancels the order's other pending payments in one conditional statement (5-60): only rows still PENDING
     * change, so a payment completed meanwhile is never overwritten; the version moves on, so a stale copy of a
     * cancelled payment cannot be saved over it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE payment SET payment_status = 'CANCELLED', failure_message = :message,"
        + " version = version + 1, last_modified_on = now()"
        + " WHERE order_id = :orderId AND payment_status = 'PENDING' AND id <> :keepId", nativeQuery = true)
    int cancelOtherPending(@Param("orderId") Long orderId, @Param("keepId") Long keepId,
                           @Param("message") String message);

    /** Transaction-scoped advisory lock on the order id: init, capture and confirm run one at a time (5-60). */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:orderId)) AS locked", nativeQuery = true)
    Integer lockOrder(@Param("orderId") Long orderId);

    /** Order accepted the completed payment. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE payment SET order_confirmed = true, failure_message = NULL, next_order_confirm_on = NULL,"
        + " version = version + 1, last_modified_on = now() WHERE id = :id", nativeQuery = true)
    int markOrderConfirmed(@Param("id") Long id);

    /** Order refused the completed payment: no more retries, flagged for a manual refund (5-61). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE payment SET order_refused = true, failure_message = :message,"
        + " next_order_confirm_on = NULL, version = version + 1, last_modified_on = now()"
        + " WHERE id = :id AND NOT order_confirmed", nativeQuery = true)
    int markOrderRefused(@Param("id") Long id, @Param("message") String message);

    /** Order could not be told (down): another try is due at {@code next}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE payment SET order_confirm_attempts = order_confirm_attempts + 1,"
        + " next_order_confirm_on = :next, version = version + 1, last_modified_on = now()"
        + " WHERE id = :id AND NOT order_confirmed", nativeQuery = true)
    int recordConfirmFailure(@Param("id") Long id, @Param("next") ZonedDateTime next);

    /**
     * Completed payments order has not accepted yet and did not refuse, due for another try (reconciler, 5-61):
     * never tried first, then the longest-waiting.
     */
    @Query(value = "SELECT id FROM payment WHERE payment_status = 'COMPLETED' AND NOT order_confirmed"
        + " AND NOT order_refused AND (next_order_confirm_on IS NULL OR next_order_confirm_on <= now())"
        + " ORDER BY next_order_confirm_on NULLS FIRST, id LIMIT :limit", nativeQuery = true)
    List<Long> findIdsToReconcile(@Param("limit") int limit);
}
