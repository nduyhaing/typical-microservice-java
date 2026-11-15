package com.shopapp.tax.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.tax.model.TaxClass;
import com.shopapp.tax.model.TaxRate;
import com.shopapp.tax.repository.TaxClassRepository;
import com.shopapp.tax.repository.TaxRateRepository;
import com.shopapp.tax.utils.Constants.ErrorCode;
import com.shopapp.tax.utils.Rates;
import com.shopapp.tax.viewmodel.taxrate.TaxRateGetDetailVm;
import com.shopapp.tax.viewmodel.taxrate.TaxRateListGetVm;
import com.shopapp.tax.viewmodel.taxrate.TaxRatePostVm;
import com.shopapp.tax.viewmodel.taxrate.TaxRateVm;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaxRateService {

    /** Most specific first: zip beats state beats country only; ties (impossible after 3c-12) by lowest id. */
    private static final Comparator<TaxRate> MORE_SPECIFIC_FIRST = Comparator
        .comparingInt(TaxRateService::specificity).reversed()
        .thenComparing(TaxRate::getId);

    private final TaxRateRepository taxRateRepository;
    private final TaxClassRepository taxClassRepository;

    @Transactional
    public TaxRate createTaxRate(TaxRatePostVm taxRatePostVm) {
        TaxRate taxRate = new TaxRate();
        apply(taxRate, taxRatePostVm);
        return taxRateRepository.save(taxRate);
    }

    @Transactional
    public void updateTaxRate(TaxRatePostVm taxRatePostVm, Long id) {
        apply(getTaxRate(id), taxRatePostVm);
    }

    @Transactional
    public void delete(Long id) {
        if (!taxRateRepository.existsById(id)) {
            throw new NotFoundException(ErrorCode.TAX_RATE_NOT_FOUND, id);
        }
        taxRateRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public TaxRateVm findById(Long id) {
        return TaxRateVm.fromModel(getTaxRate(id));
    }

    @Transactional(readOnly = true)
    public TaxRateListGetVm getPageableTaxRates(int pageNo, int pageSize) {
        Page<TaxRate> page = taxRateRepository.findAllBy(PageRequest.of(pageNo, pageSize, Sort.by("id")));
        return new TaxRateListGetVm(
            page.getContent().stream().map(TaxRateGetDetailVm::fromModel).toList(),
            page.getNumber(),
            page.getSize(),
            (int) page.getTotalElements(),
            page.getTotalPages(),
            page.isLast());
    }

    /** The most specific applicable rate in percent, 0 when none applies. */
    @Transactional(readOnly = true)
    public BigDecimal getTaxPercent(Long taxClassId, Long countryId, Long stateOrProvinceId, String zipCode) {
        return Rates.display(resolve(List.of(taxClassId), countryId, stateOrProvinceId, zipCode)
            .getOrDefault(taxClassId, Optional.empty())
            .map(TaxRate::getRate)
            .orElse(BigDecimal.ZERO));
    }

    /** The most specific applicable rate per tax class; classes without an applicable rate are left out (0 %). */
    @Transactional(readOnly = true)
    public List<TaxRateVm> getBulkTaxRate(Collection<Long> taxClassIds, Long countryId, Long stateOrProvinceId,
                                          String zipCode) {
        return resolve(new LinkedHashSet<>(taxClassIds), countryId, stateOrProvinceId, zipCode).values().stream()
            .flatMap(Optional::stream)
            .map(TaxRateVm::fromModel)
            .toList();
    }

    /**
     * A rate applies when its country matches, its state is empty or matches, and its zip code is empty or matches
     * (trimmed, case-insensitive); among those the most specific wins.
     */
    private Map<Long, Optional<TaxRate>> resolve(Collection<Long> taxClassIds, Long countryId,
                                                 Long stateOrProvinceId, String zipCode) {
        String zip = normalizeZip(zipCode);
        BinaryOperator<TaxRate> moreSpecific = (a, b) -> MORE_SPECIFIC_FIRST.compare(a, b) <= 0 ? a : b;
        return taxRateRepository.findCandidates(taxClassIds, countryId, stateOrProvinceId).stream()
            .filter(rate -> rate.getZipCode() == null || rate.getZipCode().equalsIgnoreCase(zip))
            .collect(Collectors.groupingBy(rate -> rate.getTaxClass().getId(),
                Collectors.reducing(moreSpecific)));
    }

    private static int specificity(TaxRate rate) {
        return (rate.getZipCode() != null ? 2 : 0) + (rate.getStateOrProvinceId() != null ? 1 : 0);
    }

    private void apply(TaxRate taxRate, TaxRatePostVm vm) {
        TaxClass taxClass = taxClassRepository.findById(vm.taxClassId())
            .orElseThrow(() -> new BadRequestException(ErrorCode.TAX_CLASS_NOT_FOUND, vm.taxClassId()));
        String zip = normalizeZip(vm.zipCode());
        boolean duplicate = taxRateRepository.findByTaxClassIdAndCountryId(taxClass.getId(), vm.countryId()).stream()
            .filter(other -> !other.getId().equals(taxRate.getId()))
            .anyMatch(other -> Objects.equals(other.getStateOrProvinceId(), vm.stateOrProvinceId())
                && (other.getZipCode() == null ? zip == null : other.getZipCode().equalsIgnoreCase(zip)));
        if (duplicate) {
            throw new BadRequestException(ErrorCode.TAX_RATE_ALREADY_EXISTED, taxClass.getName());
        }
        taxRate.setTaxClass(taxClass);
        taxRate.setRate(vm.rate());
        taxRate.setCountryId(vm.countryId());
        taxRate.setStateOrProvinceId(vm.stateOrProvinceId());
        taxRate.setZipCode(zip);
    }

    private TaxRate getTaxRate(Long id) {
        return taxRateRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.TAX_RATE_NOT_FOUND, id));
    }

    private static String normalizeZip(String zipCode) {
        return zipCode == null || zipCode.isBlank() ? null : zipCode.trim();
    }
}
