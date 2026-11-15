package com.shopapp.location.repository;

import com.shopapp.location.model.Address;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, Long> {

    @EntityGraph(attributePaths = {"country", "stateOrProvince", "district"})
    Optional<Address> findWithLocationById(Long id);

    @EntityGraph(attributePaths = {"country", "stateOrProvince", "district"})
    List<Address> findAllByIdInOrderById(Collection<Long> ids);

    boolean existsByCountryId(Long countryId);

    // derived name would parse "Or" as a keyword
    @Query("select count(a) > 0 from Address a where a.stateOrProvince.id = :stateOrProvinceId")
    boolean existsByStateOrProvinceId(@Param("stateOrProvinceId") Long stateOrProvinceId);
}
