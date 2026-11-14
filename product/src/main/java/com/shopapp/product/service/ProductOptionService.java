package com.shopapp.product.service;

import com.shopapp.commonlibrary.exception.DuplicatedException;
import com.shopapp.product.model.ProductOption;
import com.shopapp.product.repository.ProductOptionRepository;
import com.shopapp.product.utils.Constants;
import com.shopapp.product.viewmodel.productoption.ProductOptionGetVm;
import com.shopapp.product.viewmodel.productoption.ProductOptionPostVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Back office product options such as "Color" (YAS ProductOptionController/Service: list and create). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductOptionService {

    private final ProductOptionRepository productOptionRepository;

    public List<ProductOptionGetVm> getProductOptions() {
        return productOptionRepository.findAll(Sort.by("id")).stream().map(ProductOptionGetVm::fromModel).toList();
    }

    @Transactional
    public ProductOptionGetVm create(ProductOptionPostVm productOptionPostVm) {
        if (productOptionRepository.existsByName(productOptionPostVm.name())) {
            throw new DuplicatedException(Constants.ErrorCode.NAME_ALREADY_EXITED, productOptionPostVm.name());
        }
        ProductOption productOption = new ProductOption();
        productOption.setName(productOptionPostVm.name());
        return ProductOptionGetVm.fromModel(productOptionRepository.save(productOption));
    }
}
