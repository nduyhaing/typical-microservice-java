package com.shopapp.product.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.DuplicatedException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.product.model.Brand;
import com.shopapp.product.repository.BrandRepository;
import com.shopapp.product.repository.ProductRepository;
import com.shopapp.product.utils.Constants;
import com.shopapp.product.viewmodel.brand.BrandListGetVm;
import com.shopapp.product.viewmodel.brand.BrandPostVm;
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
    private final ProductRepository productRepository;

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

    public BrandVm getBrand(Long id) {
        return BrandVm.fromModel(findBrand(id));
    }

    @Transactional
    public Brand create(BrandPostVm brandPostVm) {
        validateUnique(brandPostVm, null);
        return brandRepository.save(brandPostVm.toModel());
    }

    @Transactional
    public Brand update(BrandPostVm brandPostVm, Long id) {
        Brand brand = findBrand(id);
        validateUnique(brandPostVm, id);
        brand.setSlug(brandPostVm.slug());
        brand.setName(brandPostVm.name());
        brand.setPublished(brandPostVm.isPublish());
        return brandRepository.save(brand);
    }

    /** YAS refuses to delete a brand that products still use. */
    @Transactional
    public void delete(Long id) {
        Brand brand = findBrand(id);
        if (productRepository.existsByBrandId(id)) {
            throw new BadRequestException(Constants.ErrorCode.MAKE_SURE_BRAND_DONT_CONTAINS_ANY_PRODUCT);
        }
        brandRepository.delete(brand);
    }

    private Brand findBrand(Long id) {
        return brandRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.BRAND_NOT_FOUND, id));
    }

    /** YAS checks only the name; slugs must be unique too (they address the storefront brand page). */
    private void validateUnique(BrandPostVm brandPostVm, Long id) {
        boolean nameTaken = id == null ? brandRepository.existsByName(brandPostVm.name())
            : brandRepository.existsByNameAndIdNot(brandPostVm.name(), id);
        if (nameTaken) {
            throw new DuplicatedException(Constants.ErrorCode.NAME_ALREADY_EXITED, brandPostVm.name());
        }
        boolean slugTaken = id == null ? brandRepository.existsBySlug(brandPostVm.slug())
            : brandRepository.existsBySlugAndIdNot(brandPostVm.slug(), id);
        if (slugTaken) {
            throw new DuplicatedException(Constants.ErrorCode.SLUG_ALREADY_EXISTED_OR_DUPLICATED, brandPostVm.slug());
        }
    }
}
