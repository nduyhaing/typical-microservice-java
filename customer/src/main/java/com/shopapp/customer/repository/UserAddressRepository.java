package com.shopapp.customer.repository;

import com.shopapp.customer.model.UserAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findAllByUserIdOrderByIdAsc(String userId);

    boolean existsByUserId(String userId);

    Optional<UserAddress> findOneByUserIdAndAddressId(String userId, Long addressId);

    Optional<UserAddress> findByUserIdAndIsActiveTrue(String userId);

    Optional<UserAddress> findFirstByUserIdOrderByIdAsc(String userId);

    /** First half of switching the default: two statements, as the one-default index is checked row by row. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAddress ua set ua.isActive = false where ua.userId = :userId and ua.isActive = true")
    void clearDefault(@Param("userId") String userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAddress ua set ua.isActive = true where ua.userId = :userId and ua.addressId = :addressId")
    void makeDefault(@Param("userId") String userId, @Param("addressId") Long addressId);
}
