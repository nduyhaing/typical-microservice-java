package com.shopapp.tax.repository;

import com.shopapp.tax.model.TaxClass;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxClassRepository extends JpaRepository<TaxClass, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
