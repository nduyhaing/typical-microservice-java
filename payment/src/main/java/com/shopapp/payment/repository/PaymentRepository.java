package com.shopapp.payment.repository;

import com.shopapp.payment.model.Payment;
import com.shopapp.payment.model.enumeration.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
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
}
