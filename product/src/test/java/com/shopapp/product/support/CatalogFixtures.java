package com.shopapp.product.support;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.model.ProductCategory;
import com.shopapp.product.model.ProductRelated;
import com.shopapp.product.repository.BrandRepository;
import com.shopapp.product.repository.CategoryRepository;
import com.shopapp.product.repository.ProductCategoryRepository;
import com.shopapp.product.repository.ProductRelatedRepository;
import com.shopapp.product.repository.ProductRepository;
import org.springframework.boot.test.context.TestComponent;

/**
 * Builds catalog rows for integration tests.
 */
@TestComponent
public class CatalogFixtures {

    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRelatedRepository productRelatedRepository;

    public CatalogFixtures(BrandRepository brandRepository, CategoryRepository categoryRepository,
                           ProductRepository productRepository, ProductCategoryRepository productCategoryRepository,
                           ProductRelatedRepository productRelatedRepository) {
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productRelatedRepository = productRelatedRepository;
    }

    public Brand brand(String name, String slug) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setSlug(slug);
        brand.setPublished(true);
        return brandRepository.save(brand);
    }

    public Category category(String name, String slug) {
        Category category = new Category();
        category.setName(name);
        category.setSlug(slug);
        return categoryRepository.save(category);
    }

    public Product product(String name, String slug, double price) {
        Product product = new Product();
        product.setName(name);
        product.setSlug(slug);
        product.setPrice(price);
        product.setPublished(true);
        product.setVisibleIndividually(true);
        product.setAllowedToOrder(true);
        return productRepository.save(product);
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }

    public void inCategory(Product product, Category category) {
        ProductCategory productCategory = new ProductCategory();
        productCategory.setProduct(product);
        productCategory.setCategory(category);
        productCategoryRepository.save(productCategory);
    }

    public Product ofBrand(Product product, Brand brand) {
        product.setBrand(brand);
        return productRepository.save(product);
    }

    public void related(Product product, Product relatedProduct) {
        ProductRelated productRelated = new ProductRelated();
        productRelated.setProduct(product);
        productRelated.setRelatedProduct(relatedProduct);
        productRelatedRepository.save(productRelated);
    }

    /** A variation: published, hidden from lists, child of the given parent. */
    public Product variation(Product parent, String name, String slug, double price) {
        Product variation = product(name, slug, price);
        variation.setVisibleIndividually(false);
        variation.setParent(parent);
        return productRepository.save(variation);
    }
}
