package com.shopapp.location.service;

import com.shopapp.location.repository.DistrictRepository;
import com.shopapp.location.viewmodel.district.DistrictGetVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DistrictService {

    private final DistrictRepository districtRepository;

    /** Districts of a state/province sorted by name; empty for an unknown state. */
    public List<DistrictGetVm> getList(Long stateOrProvinceId) {
        return districtRepository.findAllByStateProvinceIdOrderByNameAsc(stateOrProvinceId);
    }
}
