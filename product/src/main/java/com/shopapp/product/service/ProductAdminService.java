package com.shopapp.product.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.DuplicatedException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.model.ProductCategory;
import com.shopapp.product.model.ProductImage;
import com.shopapp.product.model.ProductOption;
import com.shopapp.product.model.ProductOptionCombination;
import com.shopapp.product.model.ProductOptionValue;
import com.shopapp.product.model.ProductRelated;
import com.shopapp.product.repository.BrandRepository;
import com.shopapp.product.repository.CategoryRepository;
import com.shopapp.product.repository.ProductCategoryRepository;
import com.shopapp.product.repository.ProductImageRepository;
import com.shopapp.product.repository.ProductOptionCombinationRepository;
import com.shopapp.product.repository.ProductOptionRepository;
import com.shopapp.product.repository.ProductOptionValueRepository;
import com.shopapp.product.repository.ProductRelatedRepository;
import com.shopapp.product.repository.ProductRepository;
import com.shopapp.product.utils.Constants;
import com.shopapp.product.viewmodel.ImageVm;
import com.shopapp.product.viewmodel.category.CategoryGetVm;
import com.shopapp.product.viewmodel.product.ProductDetailVm;
import com.shopapp.product.viewmodel.product.ProductGetDetailVm;
import com.shopapp.product.viewmodel.product.ProductListGetVm;
import com.shopapp.product.viewmodel.product.ProductListVm;
import com.shopapp.product.viewmodel.product.ProductPostVm;
import com.shopapp.product.viewmodel.product.ProductProperties;
import com.shopapp.product.viewmodel.product.ProductPutVm;
import com.shopapp.product.viewmodel.product.ProductSaveVm;
import com.shopapp.product.viewmodel.product.ProductVariationGetVm;
import com.shopapp.product.viewmodel.product.ProductVariationPutVm;
import com.shopapp.product.viewmodel.product.ProductVariationSaveVm;
import com.shopapp.product.viewmodel.productoption.ProductOptionValuePostVm;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Back office product management, ported from YAS ProductService (create/update/delete, list, detail, variations).
 * Differences from YAS are listed in decisions.md 3a-15 .. 3a-19.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAdminService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final ProductOptionCombinationRepository productOptionCombinationRepository;
    private final ProductRelatedRepository productRelatedRepository;
    private final ProductDetailService productDetailService;
    private final MediaService mediaService;

    public ProductListGetVm getProducts(int pageNo, int pageSize, String productName, String brandName) {
        Page<Product> page = productRepository.findForBackoffice(productName.trim().toLowerCase(Locale.ROOT),
            brandName.trim(), PageRequest.of(pageNo, pageSize));
        return new ProductListGetVm(
            page.getContent().stream().map(ProductListVm::fromModel).toList(),
            page.getNumber(),
            page.getSize(),
            (int) page.getTotalElements(),
            page.getTotalPages(),
            page.isLast());
    }

    public ProductDetailVm getProduct(long id) {
        Product product = findEditableProduct(id);
        List<CategoryGetVm> categories = product.getProductCategories().stream()
            .map(ProductCategory::getCategory)
            .map(category -> new CategoryGetVm(category.getId(), category.getName(), category.getSlug(),
                category.getParent() == null ? -1 : category.getParent().getId(), null))
            .toList();
        Map<Long, String> urls = mediaService.getMediaUrls(ProductDetailService.imageIds(List.of(product)));
        List<ImageVm> images = product.getProductImages().stream()
            .map(image -> new ImageVm(image.getImageId(), MediaService.urlOf(urls, image.getImageId())))
            .toList();
        Long thumbnailId = product.getThumbnailMediaId();
        return ProductDetailVm.builder()
            .id(product.getId())
            .name(product.getName())
            .shortDescription(product.getShortDescription())
            .description(product.getDescription())
            .specification(product.getSpecification())
            .sku(product.getSku())
            .gtin(product.getGtin())
            .slug(product.getSlug())
            .isAllowedToOrder(product.isAllowedToOrder())
            .isPublished(product.isPublished())
            .isFeatured(product.isFeatured())
            .isVisible(product.isVisibleIndividually())
            .stockTrackingEnabled(product.isStockTrackingEnabled())
            .weight(product.getWeight())
            .dimensionUnit(product.getDimensionUnit())
            .length(product.getLength())
            .width(product.getWidth())
            .height(product.getHeight())
            .price(product.getPrice())
            .brandId(product.getBrand() == null ? null : product.getBrand().getId())
            .categories(categories)
            .metaTitle(product.getMetaTitle())
            .metaKeyword(product.getMetaKeyword())
            .metaDescription(product.getMetaDescription())
            .thumbnailMedia(thumbnailId == null ? null
                : new ImageVm(thumbnailId, MediaService.urlOf(urls, thumbnailId)))
            .productImageMedias(images)
            .taxClassId(product.getTaxClassId())
            .parentId(product.getParent() == null ? null : product.getParent().getId())
            .build();
    }

    /**
     * YAS GET /backoffice/products/related-products/{id}: all related products, published or not, so the back office
     * can keep them when it saves a product (an update replaces the related list, decisions.md 3a-19, 3a-30).
     */
    public List<ProductListVm> getRelatedProducts(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, id));
        return product.getRelatedProducts().stream()
            .map(related -> ProductListVm.fromModel(related.getRelatedProduct()))
            .toList();
    }

    /** The parent's variations that were not deleted, whether or not the parent is published. */
    public List<ProductVariationGetVm> getVariations(Long parentId) {
        Product parent = findProduct(parentId);
        if (!parent.isHasOptions()) {
            return List.of();
        }
        return productDetailService.toVariationVms(
            parent.getProducts().stream().filter(Product::isPublished).toList());
    }

    @Transactional
    public ProductGetDetailVm create(ProductPostVm productPostVm) {
        validate(productPostVm, null);
        List<? extends ProductVariationSaveVm> variations = orEmpty(productPostVm.variations());
        final Map<Long, ProductOption> optionsById = checkOptions(productPostVm, variations);

        Product product = Product.builder().build();
        applyFields(product, productPostVm);
        product.setBrand(findBrand(productPostVm.brandId()));
        product.setHasOptions(!variations.isEmpty());
        product = productRepository.save(product);

        replaceCategories(product, productPostVm.categoryIds());
        replaceImages(product, productPostVm.productImageIds());
        replaceRelated(product, productPostVm.relatedProductIds());
        if (!variations.isEmpty()) {
            saveOptionValues(product, productPostVm.productOptionValues(), optionsById);
            createVariations(product, variations, optionsById, productPostVm.productOptionValues());
        }
        return ProductGetDetailVm.fromModel(product);
    }

    @Transactional
    public void update(long id, ProductPutVm productPutVm) {
        Product product = findEditableProduct(id);
        validate(productPutVm, product);
        List<ProductVariationPutVm> variations = orEmpty(productPutVm.variations());
        List<ProductVariationPutVm> newVariations = variations.stream().filter(v -> v.id() == null).toList();
        final Map<Long, ProductOption> optionsById = checkOptions(productPutVm, newVariations);

        applyFields(product, productPutVm);
        product.setBrand(findBrand(productPutVm.brandId()));
        replaceCategories(product, productPutVm.categoryIds());
        replaceImages(product, productPutVm.productImageIds());
        replaceRelated(product, productPutVm.relatedProductIds());

        Map<Long, Product> existingVariations = product.getProducts().stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (ProductVariationPutVm variationVm : variations) {
            if (variationVm.id() != null) {
                Product variation = existingVariations.get(variationVm.id());
                applyVariationFields(variation, variationVm);
                replaceImages(variation, variationVm.productImageIds());
            }
        }
        if (productPutVm.variations() != null) {
            deleteVariationsLeftOut(existingVariations.values(), variations);
        }
        // Variations can be ordered exactly when their parent can (YAS leaves variations "not allowed to order").
        existingVariations.values().forEach(variation -> variation.setAllowedToOrder(product.isAllowedToOrder()));

        if (!orEmpty(productPutVm.productOptionValues()).isEmpty()) {
            saveOptionValues(product, productPutVm.productOptionValues(), optionsById);
        }
        if (!newVariations.isEmpty()) {
            createVariations(product, newVariations, optionsById, productPutVm.productOptionValues());
            product.setHasOptions(true);
        } else if (productPutVm.variations() != null && product.isHasOptions()
            && existingVariations.values().stream().noneMatch(Product::isPublished)) {
            // every variation removed: the product is sold on its own again
            product.setHasOptions(false);
        }
        productRepository.save(product);
    }

    /**
     * A variation removed in the editor (left out of {@code variations}) is soft-deleted like {@link #delete}: it can
     * no longer be shown or ordered and loses its option combinations (decisions.md 3a-31). YAS ignores it.
     */
    private void deleteVariationsLeftOut(Collection<Product> existing, List<ProductVariationPutVm> requested) {
        Set<Long> keptIds = requested.stream().map(ProductVariationPutVm::id).filter(Objects::nonNull)
            .collect(Collectors.toSet());
        for (Product variation : existing) {
            if (variation.isPublished() && !keptIds.contains(variation.getId())) {
                softDelete(variation);
            }
        }
    }

    /**
     * YAS soft delete: the product is unpublished (orders and carts may still refer to it). A deleted variation also
     * loses its option combinations, so its values disappear from the storefront option list.
     */
    @Transactional
    public void delete(Long id) {
        softDelete(findProduct(id));
    }

    private void softDelete(Product product) {
        product.setPublished(false);
        if (product.getParent() != null) {
            productOptionCombinationRepository.deleteAll(productOptionCombinationRepository.findAllByProduct(product));
        }
        productRepository.save(product);
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, id));
    }

    /** A variation is edited through its parent's form, never on its own (decisions.md 3a-34). */
    private Product findEditableProduct(Long id) {
        Product product = findProduct(id);
        if (product.getParent() != null) {
            throw new BadRequestException(Constants.ErrorCode.EDIT_THE_PARENT_PRODUCT, id);
        }
        return product;
    }

    private Brand findBrand(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return brandRepository.findById(brandId)
            .orElseThrow(() -> new BadRequestException(Constants.ErrorCode.BRAND_NOT_FOUND, brandId));
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    // ---- validation --------------------------------------------------------------------------------------------

    private void validate(ProductSaveVm<? extends ProductVariationSaveVm> productSaveVm, Product existing) {
        if (productSaveVm.length() != null && productSaveVm.width() != null
            && productSaveVm.length() < productSaveVm.width()) {
            throw new BadRequestException(Constants.ErrorCode.MAKE_SURE_LENGTH_GREATER_THAN_WIDTH);
        }
        List<? extends ProductVariationSaveVm> variations = orEmpty(productSaveVm.variations());
        if (existing != null) {
            Set<Long> childIds = existing.getProducts().stream().map(Product::getId).collect(Collectors.toSet());
            variations.stream().map(ProductProperties::id).filter(Objects::nonNull)
                .filter(variationId -> !childIds.contains(variationId))
                .findFirst()
                .ifPresent(variationId -> {
                    throw new BadRequestException(Constants.ErrorCode.PRODUCT_NOT_FOUND, variationId);
                });
        }
        validateNoDuplicatesInRequest(productSaveVm, variations);
        validateUniqueInDatabase(productSaveVm, existing == null ? null : existing.getId());
        for (ProductVariationSaveVm variation : variations) {
            validateUniqueInDatabase(variation, variation.id());
        }
    }

    /** The product and its variations must not repeat a slug (case-insensitive), sku or gtin among themselves. */
    private static void validateNoDuplicatesInRequest(ProductProperties product,
                                                      List<? extends ProductVariationSaveVm> variations) {
        Set<String> slugs = new HashSet<>();
        Set<String> skus = new HashSet<>();
        Set<String> gtins = new HashSet<>();
        List<ProductProperties> all = new ArrayList<>();
        all.add(product);
        all.addAll(variations);
        for (ProductProperties properties : all) {
            if (!slugs.add(properties.slug().toLowerCase(Locale.ROOT))) {
                throw new DuplicatedException(Constants.ErrorCode.SLUG_ALREADY_EXISTED_OR_DUPLICATED,
                    properties.slug());
            }
            if (StringUtils.hasText(properties.sku()) && !skus.add(properties.sku())) {
                throw new DuplicatedException(Constants.ErrorCode.SKU_ALREADY_EXISTED_OR_DUPLICATED, properties.sku());
            }
            if (StringUtils.hasText(properties.gtin()) && !gtins.add(properties.gtin())) {
                throw new DuplicatedException(Constants.ErrorCode.GTIN_ALREADY_EXISTED_OR_DUPLICATED,
                    properties.gtin());
            }
        }
    }

    /** Slug, sku and gtin must not belong to any other product, published or not (YAS: published only). */
    private void validateUniqueInDatabase(ProductProperties properties, Long selfId) {
        String slug = properties.slug().toLowerCase(Locale.ROOT);
        if (selfId == null ? productRepository.existsBySlug(slug)
            : productRepository.existsBySlugAndIdNot(slug, selfId)) {
            throw new DuplicatedException(Constants.ErrorCode.SLUG_ALREADY_EXISTED_OR_DUPLICATED, slug);
        }
        String sku = properties.sku();
        if (StringUtils.hasText(sku) && (selfId == null ? productRepository.existsBySku(sku)
            : productRepository.existsBySkuAndIdNot(sku, selfId))) {
            throw new DuplicatedException(Constants.ErrorCode.SKU_ALREADY_EXISTED_OR_DUPLICATED, sku);
        }
        String gtin = properties.gtin();
        if (StringUtils.hasText(gtin) && (selfId == null ? productRepository.existsByGtin(gtin)
            : productRepository.existsByGtinAndIdNot(gtin, selfId))) {
            throw new DuplicatedException(Constants.ErrorCode.GTIN_ALREADY_EXISTED_OR_DUPLICATED, gtin);
        }
    }

    /**
     * Loads the options named in {@code productOptionValues}; every one must exist, and each new variation must use
     * only listed options and values. Variations without any option values are refused (YAS silently drops them).
     */
    private Map<Long, ProductOption> checkOptions(ProductSaveVm<? extends ProductVariationSaveVm> productSaveVm,
                                                  List<? extends ProductVariationSaveVm> newVariations) {
        List<ProductOptionValuePostVm> optionValues = orEmpty(productSaveVm.productOptionValues());
        if (optionValues.isEmpty()) {
            if (!newVariations.isEmpty()) {
                throw new BadRequestException(Constants.ErrorCode.NO_MATCHING_PRODUCT_OPTIONS);
            }
            return Map.of();
        }
        Set<Long> optionIds = optionValues.stream().map(ProductOptionValuePostVm::productOptionId)
            .collect(Collectors.toSet());
        Map<Long, ProductOption> optionsById = productOptionRepository.findAllById(optionIds).stream()
            .collect(Collectors.toMap(ProductOption::getId, Function.identity()));
        if (optionsById.size() != optionIds.size()) {
            throw new BadRequestException(Constants.ErrorCode.NO_MATCHING_PRODUCT_OPTIONS);
        }
        Map<Long, Set<String>> allowedValues = new HashMap<>();
        optionValues.forEach(vm -> allowedValues.computeIfAbsent(vm.productOptionId(), key -> new HashSet<>())
            .addAll(orEmpty(vm.value())));
        for (ProductVariationSaveVm variation : newVariations) {
            Map<Long, String> values = variation.optionValuesByOptionId();
            if (values == null || values.isEmpty()) {
                throw new BadRequestException(Constants.ErrorCode.NO_MATCHING_PRODUCT_OPTIONS);
            }
            values.forEach((optionId, value) -> {
                if (!allowedValues.getOrDefault(optionId, Set.of()).contains(value)) {
                    throw new BadRequestException(Constants.ErrorCode.PRODUCT_OPTION_VALUE_IS_NOT_FOUND, value);
                }
            });
        }
        return optionsById;
    }

    // ---- writing -----------------------------------------------------------------------------------------------

    private static void applyFields(Product product, ProductSaveVm<? extends ProductVariationSaveVm> vm) {
        product.setName(vm.name());
        product.setSlug(vm.slug().toLowerCase(Locale.ROOT));
        product.setThumbnailMediaId(vm.thumbnailMediaId());
        // YAS create swaps description and short description and copies description into metaDescription.
        product.setShortDescription(vm.shortDescription());
        product.setDescription(vm.description());
        product.setSpecification(vm.specification());
        product.setSku(blankToNull(vm.sku()));
        product.setGtin(blankToNull(vm.gtin()));
        product.setPrice(vm.price());
        product.setAllowedToOrder(isTrue(vm.isAllowedToOrder()));
        product.setPublished(isTrue(vm.isPublished()));
        product.setFeatured(isTrue(vm.isFeatured()));
        product.setVisibleIndividually(isTrue(vm.isVisibleIndividually()));
        product.setStockTrackingEnabled(isTrue(vm.stockTrackingEnabled()));
        product.setMetaTitle(vm.metaTitle());
        product.setMetaKeyword(vm.metaKeyword());
        product.setMetaDescription(vm.metaDescription());
        product.setTaxClassId(vm.taxClassId());
        product.setWeight(vm.weight());
        product.setDimensionUnit(vm.dimensionUnit());
        product.setLength(vm.length());
        product.setWidth(vm.width());
        product.setHeight(vm.height());
    }

    private static void applyVariationFields(Product variation, ProductVariationSaveVm vm) {
        variation.setName(vm.name());
        variation.setSlug(vm.slug().toLowerCase(Locale.ROOT));
        variation.setSku(blankToNull(vm.sku()));
        variation.setGtin(blankToNull(vm.gtin()));
        variation.setPrice(vm.price());
        variation.setThumbnailMediaId(vm.thumbnailMediaId());
    }

    /** Replaces the product's categories; every id must exist (YAS keeps none when the list did not change). */
    private void replaceCategories(Product product, List<Long> categoryIds) {
        List<Long> wanted = orEmpty(categoryIds).stream().distinct().toList();
        List<Category> categories = categoryRepository.findAllById(wanted);
        if (categories.size() != wanted.size()) {
            Set<Long> found = categories.stream().map(Category::getId).collect(Collectors.toSet());
            throw new BadRequestException(Constants.ErrorCode.CATEGORY_NOT_FOUND,
                wanted.stream().filter(categoryId -> !found.contains(categoryId)).toList());
        }
        List<ProductCategory> old = productCategoryRepository.findAllByProductId(product.getId());
        product.getProductCategories().clear();
        productCategoryRepository.deleteAll(old);
        List<ProductCategory> created = productCategoryRepository.saveAll(categories.stream()
            .map(category -> ProductCategory.builder().product(product).category(category).build())
            .toList());
        product.getProductCategories().addAll(created);
    }

    /** Stores media ids as given (the media service arrives in slice 3b). */
    private void replaceImages(Product product, List<Long> imageIds) {
        List<ProductImage> old = new ArrayList<>(product.getProductImages());
        product.getProductImages().clear();
        productImageRepository.deleteAll(old);
        List<ProductImage> created = productImageRepository.saveAll(orEmpty(imageIds).stream().distinct()
            .map(imageId -> ProductImage.builder().imageId(imageId).product(product).build())
            .toList());
        product.getProductImages().addAll(created);
    }

    /** Replaces related products; unknown ids are ignored (YAS) and a product is never related to itself. */
    private void replaceRelated(Product product, List<Long> relatedProductIds) {
        List<ProductRelated> old = new ArrayList<>(product.getRelatedProducts());
        product.getRelatedProducts().clear();
        productRelatedRepository.deleteAll(old);
        List<Long> wanted = orEmpty(relatedProductIds).stream().distinct()
            .filter(relatedId -> !relatedId.equals(product.getId())).toList();
        List<ProductRelated> created = productRelatedRepository.saveAll(productRepository.findAllById(wanted).stream()
            .map(related -> ProductRelated.builder().product(product).relatedProduct(related).build())
            .toList());
        product.getRelatedProducts().addAll(created);
    }

    /** One option value row per (option, value), replacing the product's previous rows. */
    private void saveOptionValues(Product product, List<ProductOptionValuePostVm> optionValueVms,
                                  Map<Long, ProductOption> optionsById) {
        productOptionValueRepository.deleteAllByProductId(product.getId());
        productOptionValueRepository.flush();
        List<ProductOptionValue> rows = new ArrayList<>();
        for (ProductOptionValuePostVm vm : orEmpty(optionValueVms)) {
            for (String value : orEmpty(vm.value()).stream().distinct().toList()) {
                rows.add(ProductOptionValue.builder()
                    .product(product)
                    .productOption(optionsById.get(vm.productOptionId()))
                    .displayType(vm.displayType())
                    .displayOrder(displayOrder(vm))
                    .value(value)
                    .build());
            }
        }
        productOptionValueRepository.saveAll(rows);
    }

    private static int displayOrder(ProductOptionValuePostVm vm) {
        return vm.displayOrder() == null ? 0 : vm.displayOrder();
    }

    /**
     * New variations: listed only through the parent, active ({@code isPublished}: the parent's flag controls
     * visibility), orderable when the parent is, one option combination per option value.
     */
    private void createVariations(Product parent, List<? extends ProductVariationSaveVm> variationVms,
                                  Map<Long, ProductOption> optionsById,
                                  List<ProductOptionValuePostVm> optionValueVms) {
        Map<Long, Integer> displayOrderByOption = new HashMap<>();
        optionValueVms.forEach(vm -> displayOrderByOption.putIfAbsent(vm.productOptionId(), displayOrder(vm)));
        for (ProductVariationSaveVm vm : variationVms) {
            Product variation = Product.builder()
                .parent(parent)
                .isPublished(true)
                .isVisibleIndividually(false)
                .isAllowedToOrder(parent.isAllowedToOrder())
                .build();
            applyVariationFields(variation, vm);
            variation = productRepository.save(variation);
            replaceImages(variation, vm.productImageIds());
            Product savedVariation = variation;
            List<ProductOptionCombination> combinations = vm.optionValuesByOptionId().entrySet().stream()
                .map(entry -> ProductOptionCombination.builder()
                    .product(savedVariation)
                    .productOption(optionsById.get(entry.getKey()))
                    .value(entry.getValue())
                    .displayOrder(displayOrderByOption.getOrDefault(entry.getKey(), 0))
                    .build())
                .toList();
            productOptionCombinationRepository.saveAll(combinations);
        }
    }
}
