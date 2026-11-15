package com.shopapp.location.repository;

import com.shopapp.location.model.District;
import com.shopapp.location.viewmodel.district.DistrictGetVm;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistrictRepository extends JpaRepository<District, Long> {

    List<DistrictGetVm> findAllByStateProvinceIdOrderByNameAsc(Long stateProvinceId);

    boolean existsByStateProvinceId(Long stateProvinceId);
}
