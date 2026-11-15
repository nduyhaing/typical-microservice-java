package com.shopapp.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopapp.recommendation.viewmodel.ProductDetailVm;
import com.shopapp.recommendation.viewmodel.ProductEsDetailVm;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductDocumentFormatterTest {

    private static ProductEsDetailVm summary(String name, String brand, List<String> categories) {
        return new ProductEsDetailVm(1L, name, "slug", 10.0, true, true, true, false, null, "", brand, categories,
            List.of("Screen size"), null, null);
    }

    @Test
    void format_categoriesFirstAndLast_aroundNameBrandAndShortDescription() {
        ProductDetailVm detail = new ProductDetailVm(1L, "Galaxy S24", "Samsung flagship phone.");

        String text = ProductDocumentFormatter.format(summary("Galaxy S24", "Samsung", List.of("Phones", "Samsung")),
            detail);

        assertThat(text).isEqualTo("Phones, Samsung. Galaxy S24. Brand: Samsung. Categories: Phones, Samsung. "
            + "Samsung flagship phone. Phones, Samsung");
    }

    @Test
    void format_missingPartsAreLeftOut_andHtmlRemoved() {
        ProductDetailVm detail = new ProductDetailVm(1L, "Cable", "  <b>Braided</b>\n  cable  ");

        String text = ProductDocumentFormatter.format(summary("USB-C cable", null, null), detail);

        assertThat(text).isEqualTo("USB-C cable. Braided cable");
    }

    @Test
    void format_longTextIsCut() {
        ProductDetailVm detail = new ProductDetailVm(1L, "X", "a ".repeat(5000));

        assertThat(ProductDocumentFormatter.format(summary("X", "B", List.of()), detail))
            .hasSize(ProductDocumentFormatter.MAX_LENGTH);
    }
}
