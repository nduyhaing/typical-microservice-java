package com.shopapp.product.service;

import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.product.model.Product;
import com.shopapp.product.model.ProductImage;
import com.shopapp.product.model.ProductOptionCombination;
import com.shopapp.product.model.attribute.ProductAttributeGroup;
import com.shopapp.product.model.attribute.ProductAttributeValue;
import com.shopapp.product.repository.ProductOptionCombinationRepository;
import com.shopapp.product.repository.ProductRepository;
import com.shopapp.product.utils.Constants;
import com.shopapp.product.viewmodel.ImageVm;
import com.shopapp.product.viewmodel.product.ProductDetailGetVm;
import com.shopapp.product.viewmodel.product.ProductVariationGetVm;
import com.shopapp.product.viewmodel.productattribute.ProductAttributeGroupGetVm;
import com.shopapp.product.viewmodel.productattribute.ProductAttributeValueVm;
import com.shopapp.product.viewmodel.productoption.ProductOptionCombinationGetVm;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storefront product detail page data (ported from YAS ProductService and ProductOptionCombinationController). Image
 * URLs are resolved after the read-only transaction (decisions.md 3b-19).
 */
@Service
@RequiredArgsConstructor
public class ProductDetailService {

    static final String NONE_GROUP = "None group";

    private final ProductRepository productRepository;
    private final ProductOptionCombinationRepository productOptionCombinationRepository;
    private final MediaUrlResolver mediaUrlResolver;

    public ProductDetailGetVm getProductDetail(String slug) {
        return mediaUrlResolver.readThenResolve(() -> {
            Product product = productRepository.findBySlugAndIsPublishedTrueAndIsVisibleIndividuallyTrue(slug)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, slug));
            Long thumbnailId = product.getThumbnailMediaId();
            List<Long> galleryIds = product.getProductImages().stream().map(ProductImage::getImageId).toList();
            ProductDetailGetVm detail = new ProductDetailGetVm(
                product.getId(),
                product.getName(),
                product.getBrand() == null ? null : product.getBrand().getName(),
                product.getProductCategories().stream().map(pc -> pc.getCategory().getName()).toList(),
                attributeGroups(product.getAttributeValues()),
                product.getShortDescription(),
                product.getDescription(),
                product.getSpecification(),
                product.isAllowedToOrder(),
                product.isPublished(),
                product.isFeatured(),
                product.isHasOptions(),
                product.getPrice(),
                "",
                List.of(),
                product.isStockTrackingEnabled(),
                product.stockAvailable(),
                product.trackedStockQuantity());
            return new WithMedia<>(imageIds(List.of(product)), urls -> detail.withImageUrls(
                MediaService.urlOf(urls, thumbnailId),
                galleryIds.stream().map(id -> MediaService.urlOf(urls, id)).toList()));
        });
    }

    public List<ProductVariationGetVm> getProductVariationsByParentId(Long id) {
        return mediaUrlResolver.readThenResolve(() -> {
            Product parent = productRepository.findStorefrontById(id)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, id));
            if (!parent.isHasOptions()) {
                return WithMedia.of(List.<ProductVariationGetVm>of());
            }
            return variationVms(parent.getProducts().stream().filter(Product::isPublished).toList());
        });
    }

    /** The distinct option/value pairs used by the product's variations (YAS de-duplicates the same way). */
    @Transactional(readOnly = true)
    public List<ProductOptionCombinationGetVm> getOptionValues(Long productId) {
        Product product = productRepository.findStorefrontById(productId)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, productId));
        Map<List<Object>, ProductOptionCombinationGetVm> distinct = new LinkedHashMap<>();
        productOptionCombinationRepository.findAllByParentProductId(product.getId()).stream()
            .map(ProductOptionCombinationGetVm::fromModel)
            .forEach(vm -> distinct.putIfAbsent(List.of(vm.productOptionId(), vm.productOptionValue()), vm));
        return List.copyOf(distinct.values());
    }

    /** Attribute values grouped by attribute group, in first-seen order; ungrouped values go to "None group". */
    private List<ProductAttributeGroupGetVm> attributeGroups(List<ProductAttributeValue> values) {
        Map<String, List<ProductAttributeValueVm>> byGroupName = new LinkedHashMap<>();
        for (ProductAttributeValue value : values) {
            ProductAttributeGroup group = value.getProductAttribute().getProductAttributeGroup();
            String groupName = group == null ? NONE_GROUP : group.getName();
            byGroupName.computeIfAbsent(groupName, name -> new ArrayList<>()).add(toValueVm(value));
        }
        return byGroupName.entrySet().stream()
            .map(entry -> new ProductAttributeGroupGetVm(entry.getKey(), entry.getValue()))
            .toList();
    }

    private ProductAttributeValueVm toValueVm(ProductAttributeValue value) {
        return new ProductAttributeValueVm(value.getProductAttribute().getName(), value.getValue());
    }

    /**
     * Variation view models, read inside the caller's transaction; their images are resolved afterwards with one
     * media-service call (decisions.md 3b-10, 3b-19). Also used by the back office variation list.
     */
    WithMedia<List<ProductVariationGetVm>> variationVms(List<Product> variations) {
        List<ProductVariationGetVm> vms = variations.stream().map(this::toVariationVm).toList();
        return new WithMedia<>(imageIds(variations),
            urls -> vms.stream().map(vm -> vm.withImageUrls(urls)).toList());
    }

    /** Thumbnail and gallery media ids of the given products. */
    static List<Long> imageIds(List<Product> products) {
        List<Long> ids = new ArrayList<>();
        for (Product product : products) {
            ids.add(product.getThumbnailMediaId());
            product.getProductImages().forEach(image -> ids.add(image.getImageId()));
        }
        return ids;
    }

    /** A variation view model whose images have empty URLs, filled in by {@link #variationVms(List)}. */
    private ProductVariationGetVm toVariationVm(Product variation) {
        Map<Long, String> options = productOptionCombinationRepository.findAllByProduct(variation).stream()
            .collect(Collectors.toMap(c -> c.getProductOption().getId(), ProductOptionCombination::getValue));
        Long thumbnailId = variation.getThumbnailMediaId();
        ImageVm thumbnail = thumbnailId == null ? null
            : new ImageVm(thumbnailId, "");
        List<ImageVm> images = variation.getProductImages().stream()
            .map(image -> new ImageVm(image.getImageId(), ""))
            .toList();
        return new ProductVariationGetVm(variation.getId(), variation.getName(), variation.getSlug(),
            variation.getSku(), variation.getGtin(), variation.getPrice(), thumbnail, images, options);
    }
}
