package com.shopapp.tax.repository;

import com.shopapp.tax.model.TaxRate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

    boolean existsByTaxClassId(Long taxClassId);

    @EntityGraph(attributePaths = "taxClass")
    Page<TaxRate> findAllBy(Pageable pageable);

    /**
     * Rates of the given classes that may apply in the country and state: country-wide or for that state. Zip
     * codes are matched (and ranked) by the caller.
     */
    @Query("""
        SELECT tr FROM TaxRate tr
        WHERE tr.countryId = :countryId
        AND (tr.stateOrProvinceId IS NULL OR tr.stateOrProvinceId = :stateOrProvinceId)
        AND tr.taxClass.id IN :taxClassIds
        """)
    List<TaxRate> findCandidates(@Param("taxClassIds") Collection<Long> taxClassIds,
                                 @Param("countryId") Long countryId,
                                 @Param("stateOrProvinceId") Long stateOrProvinceId);

    /** Rates of a class in a country (any state, any zip); used to refuse a second rate at the same location. */
    List<TaxRate> findByTaxClassIdAndCountryId(Long taxClassId, Long countryId);
}
