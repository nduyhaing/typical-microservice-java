package com.shopapp.tax.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.DuplicatedException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.tax.model.TaxClass;
import com.shopapp.tax.repository.TaxClassRepository;
import com.shopapp.tax.repository.TaxRateRepository;
import com.shopapp.tax.utils.Constants.ErrorCode;
import com.shopapp.tax.viewmodel.taxclass.TaxClassListGetVm;
import com.shopapp.tax.viewmodel.taxclass.TaxClassPostVm;
import com.shopapp.tax.viewmodel.taxclass.TaxClassVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaxClassService {

    private final TaxClassRepository taxClassRepository;
    private final TaxRateRepository taxRateRepository;

    @Transactional(readOnly = true)
    public List<TaxClassVm> findAllTaxClasses() {
        return taxClassRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
            .map(TaxClassVm::fromModel)
            .toList();
    }

    @Transactional(readOnly = true)
    public TaxClassVm findById(Long id) {
        return TaxClassVm.fromModel(getTaxClass(id));
    }

    @Transactional
    public TaxClass create(TaxClassPostVm taxClassPostVm) {
        String name = taxClassPostVm.name().trim();
        if (taxClassRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicatedException(ErrorCode.NAME_ALREADY_EXITED, name);
        }
        return taxClassRepository.save(new TaxClass(name));
    }

    @Transactional
    public void update(TaxClassPostVm taxClassPostVm, Long id) {
        TaxClass taxClass = getTaxClass(id);
        String name = taxClassPostVm.name().trim();
        if (taxClassRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicatedException(ErrorCode.NAME_ALREADY_EXITED, name);
        }
        taxClass.setName(name);
    }

    /** A class used by rates cannot be deleted (YAS: the foreign key failed with a 500). */
    @Transactional
    public void delete(Long id) {
        if (!taxClassRepository.existsById(id)) {
            throw new NotFoundException(ErrorCode.TAX_CLASS_NOT_FOUND, id);
        }
        if (taxRateRepository.existsByTaxClassId(id)) {
            throw new BadRequestException(ErrorCode.TAX_CLASS_IN_USE, id);
        }
        taxClassRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public TaxClassListGetVm getPageableTaxClasses(int pageNo, int pageSize) {
        Page<TaxClass> page = taxClassRepository.findAll(PageRequest.of(pageNo, pageSize, Sort.by("id")));
        return new TaxClassListGetVm(
            page.getContent().stream().map(TaxClassVm::fromModel).toList(),
            page.getNumber(),
            page.getSize(),
            (int) page.getTotalElements(),
            page.getTotalPages(),
            page.isLast());
    }

    private TaxClass getTaxClass(Long id) {
        return taxClassRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.TAX_CLASS_NOT_FOUND, id));
    }
}
