package com.shopapp.product.service;

import com.shopapp.product.model.Brand;
import com.shopapp.product.repository.BrandRepository;
import com.shopapp.product.viewmodel.brand.BrandListGetVm;
import com.shopapp.product.viewmodel.brand.BrandVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;

    public List<BrandVm> getBrands(String brandName) {
        return brandRepository.findByNameContainingIgnoreCase(brandName).stream().map(BrandVm::fromModel).toList();
    }

    public BrandListGetVm getBrands(int pageNo, int pageSize) {
        Page<Brand> brandPage = brandRepository.findAll(PageRequest.of(pageNo, pageSize));
        return new BrandListGetVm(
            brandPage.getContent().stream().map(BrandVm::fromModel).toList(),
            brandPage.getNumber(),
            brandPage.getSize(),
            (int) brandPage.getTotalElements(),
            brandPage.getTotalPages(),
            brandPage.isLast());
    }
}
