package com.shopapp.order.repository;

import com.shopapp.order.model.Checkout;
import com.shopapp.order.model.enumeration.CheckoutState;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckoutRepository extends JpaRepository<Checkout, String> {
    Optional<Checkout> findByIdAndCheckoutState(String id, CheckoutState state);

    /** Locks the checkout row so concurrent "place order" calls for it run one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Checkout c WHERE c.id = :id")
    Optional<Checkout> findWithLockById(@Param("id") String id);
}
