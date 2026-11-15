package com.shopapp.location.repository;

import com.shopapp.location.model.StateOrProvince;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StateOrProvinceRepository extends JpaRepository<StateOrProvince, Long> {

    Page<StateOrProvince> findAllByCountryId(Long countryId, Pageable pageable);

    @EntityGraph(attributePaths = "country")
    List<StateOrProvince> findByIdIn(Collection<Long> ids);

    List<StateOrProvince> findAllByCountryIdOrderByNameAsc(Long countryId);

    boolean existsByCountryId(Long countryId);

    boolean existsByNameIgnoreCaseAndCountryId(String name, Long countryId);

    boolean existsByNameIgnoreCaseAndCountryIdAndIdNot(String name, Long countryId, Long excludedId);
}
