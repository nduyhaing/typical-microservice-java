package com.shopapp.product.support;

import com.shopapp.product.model.Product;
import com.shopapp.product.model.ProductOption;
import com.shopapp.product.model.ProductOptionCombination;
import com.shopapp.product.model.attribute.ProductAttribute;
import com.shopapp.product.model.attribute.ProductAttributeGroup;
import com.shopapp.product.model.attribute.ProductAttributeValue;
import com.shopapp.product.repository.ProductAttributeGroupRepository;
import com.shopapp.product.repository.ProductAttributeRepository;
import com.shopapp.product.repository.ProductAttributeValueRepository;
import com.shopapp.product.repository.ProductOptionCombinationRepository;
import com.shopapp.product.repository.ProductOptionRepository;
import org.springframework.boot.test.context.TestComponent;

/**
 * Builds attribute and option rows for product detail tests.
 */
@TestComponent
public class DetailFixtures {

    private final ProductAttributeGroupRepository groupRepository;
    private final ProductAttributeRepository attributeRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ProductOptionRepository optionRepository;
    private final ProductOptionCombinationRepository combinationRepository;

    public DetailFixtures(ProductAttributeGroupRepository groupRepository,
                          ProductAttributeRepository attributeRepository,
                          ProductAttributeValueRepository attributeValueRepository,
                          ProductOptionRepository optionRepository,
                          ProductOptionCombinationRepository combinationRepository) {
        this.groupRepository = groupRepository;
        this.attributeRepository = attributeRepository;
        this.attributeValueRepository = attributeValueRepository;
        this.optionRepository = optionRepository;
        this.combinationRepository = combinationRepository;
    }

    /** Gives the product an attribute value, creating the group and attribute. */
    public void attribute(Product product, String groupName, String attributeName, String value) {
        ProductAttributeGroup group = groupRepository.findAll().stream()
            .filter(existing -> existing.getName().equals(groupName)).findFirst()
            .orElseGet(() -> {
                ProductAttributeGroup created = new ProductAttributeGroup();
                created.setName(groupName);
                return groupRepository.save(created);
            });
        ProductAttribute attribute = new ProductAttribute();
        attribute.setName(attributeName);
        attribute.setProductAttributeGroup(group);
        attribute = attributeRepository.save(attribute);
        ProductAttributeValue attributeValue = new ProductAttributeValue();
        attributeValue.setProduct(product);
        attributeValue.setProductAttribute(attribute);
        attributeValue.setValue(value);
        attributeValueRepository.save(attributeValue);
    }

    public ProductOption option(String name) {
        ProductOption option = new ProductOption();
        option.setName(name);
        return optionRepository.save(option);
    }

    /** Records that the variation has the given value for the option. */
    public void combination(Product variation, ProductOption option, String value) {
        combinationRepository.save(ProductOptionCombination.builder()
            .product(variation).productOption(option).value(value).displayOrder(0).build());
    }
}
