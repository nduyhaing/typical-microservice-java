package com.shopapp.product.repository;

import com.shopapp.product.model.StockReservation;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    boolean existsByReference(String reference);

    /** Locked, so two concurrent restores of the same reference cannot both give the units back. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StockReservation> findByReferenceAndOwnerOrderByProductIdAsc(String reference, String owner);
}
