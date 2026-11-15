package com.shopapp.location.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.DuplicatedException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.location.model.StateOrProvince;
import com.shopapp.location.repository.AddressRepository;
import com.shopapp.location.repository.CountryRepository;
import com.shopapp.location.repository.DistrictRepository;
import com.shopapp.location.repository.StateOrProvinceRepository;
import com.shopapp.location.utils.Constants;
import com.shopapp.location.viewmodel.stateorprovince.StateOrProvinceAndCountryGetNameVm;
import com.shopapp.location.viewmodel.stateorprovince.StateOrProvinceListGetVm;
import com.shopapp.location.viewmodel.stateorprovince.StateOrProvincePostVm;
import com.shopapp.location.viewmodel.stateorprovince.StateOrProvinceVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class StateOrProvinceService {

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id"));

    private final StateOrProvinceRepository stateOrProvinceRepository;
    private final CountryRepository countryRepository;
    private final DistrictRepository districtRepository;
    private final AddressRepository addressRepository;

    public StateOrProvince createStateOrProvince(StateOrProvincePostVm request) {
        Long countryId = request.countryId();
        if (countryId == null) {
            throw new BadRequestException(Constants.ErrorCode.COUNTRY_ID_REQUIRED);
        }
        // an unknown country in the body is a bad request, not a missing resource (YAS: 404)
        if (!countryRepository.existsById(countryId)) {
            throw new BadRequestException(Constants.ErrorCode.COUNTRY_NOT_FOUND, countryId);
        }
        String name = request.name().trim();
        if (stateOrProvinceRepository.existsByNameIgnoreCaseAndCountryId(name, countryId)) {
            throw new DuplicatedException(Constants.ErrorCode.NAME_ALREADY_EXITED, name);
        }
        StateOrProvince state = new StateOrProvince();
        state.setCountry(countryRepository.getReferenceById(countryId));
        apply(state, request);
        return stateOrProvinceRepository.saveAndFlush(state);
    }

    public void updateStateOrProvince(StateOrProvincePostVm request, Long id) {
        StateOrProvince state = getState(id);
        String name = request.name().trim();
        if (stateOrProvinceRepository.existsByNameIgnoreCaseAndCountryIdAndIdNot(name, state.getCountry().getId(),
            id)) {
            throw new DuplicatedException(Constants.ErrorCode.NAME_ALREADY_EXITED, name);
        }
        apply(state, request);
        stateOrProvinceRepository.saveAndFlush(state);
    }

    public void delete(Long id) {
        if (!stateOrProvinceRepository.existsById(id)) {
            throw new NotFoundException(Constants.ErrorCode.STATE_OR_PROVINCE_NOT_FOUND, id);
        }
        if (districtRepository.existsByStateProvinceId(id) || addressRepository.existsByStateOrProvinceId(id)) {
            throw new BadRequestException(Constants.ErrorCode.STATE_OR_PROVINCE_IN_USE, id);
        }
        stateOrProvinceRepository.deleteById(id);
        stateOrProvinceRepository.flush();
    }

    @Transactional(readOnly = true)
    public StateOrProvinceVm findById(Long id) {
        return StateOrProvinceVm.fromModel(getState(id));
    }

    @Transactional(readOnly = true)
    public List<StateOrProvinceAndCountryGetNameVm> getStateOrProvinceAndCountryNames(List<Long> ids) {
        return stateOrProvinceRepository.findByIdIn(ids).stream()
            .map(StateOrProvinceAndCountryGetNameVm::fromModel)
            .toList();
    }

    /**
     * Page of states sorted by name; all countries when {@code countryId} is null (YAS: no rows then).
     */
    @Transactional(readOnly = true)
    public StateOrProvinceListGetVm getPageableStateOrProvinces(int pageNo, int pageSize, Long countryId) {
        Pageable pageable = PageRequest.of(pageNo, pageSize, BY_NAME);
        Page<StateOrProvince> page = countryId == null
            ? stateOrProvinceRepository.findAll(pageable)
            : stateOrProvinceRepository.findAllByCountryId(countryId, pageable);
        return new StateOrProvinceListGetVm(
            page.getContent().stream().map(StateOrProvinceVm::fromModel).toList(),
            page.getNumber(),
            page.getSize(),
            (int) page.getTotalElements(),
            page.getTotalPages(),
            page.isLast());
    }

    /**
     * States of one country sorted by name; all states when {@code countryId} is null (YAS: none).
     */
    @Transactional(readOnly = true)
    public List<StateOrProvinceVm> getAllByCountryId(Long countryId) {
        List<StateOrProvince> states = countryId == null
            ? stateOrProvinceRepository.findAll(BY_NAME)
            : stateOrProvinceRepository.findAllByCountryIdOrderByNameAsc(countryId);
        return states.stream().map(StateOrProvinceVm::fromModel).toList();
    }

    private StateOrProvince getState(Long id) {
        return stateOrProvinceRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.STATE_OR_PROVINCE_NOT_FOUND, id));
    }

    private static void apply(StateOrProvince state, StateOrProvincePostVm request) {
        state.setName(request.name().trim());
        state.setCode(blankToNull(request.code()));
        state.setType(blankToNull(request.type()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
