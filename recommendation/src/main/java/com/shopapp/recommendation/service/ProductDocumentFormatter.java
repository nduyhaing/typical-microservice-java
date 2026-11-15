package com.shopapp.recommendation.service;

import com.shopapp.recommendation.viewmodel.ProductDetailVm;
import com.shopapp.recommendation.viewmodel.ProductEsDetailVm;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The text that is embedded for a product (YAS ProductDocument/ProductDocumentFormatter):
 * {@code <categories>. <name>. Brand: <brand>. Categories: <categories>. <short description>. <categories>}.
 * The categories open and close the text so that the kind of product weighs more than shared wording; attribute
 * values and the specification are left out (YAS embeds them): measured on the sample catalog, "Screen size: 6.2
 * inch, Resolution: …" made a laptop the second-nearest product to a phone (decisions.md 5-43). Price is left out
 * too. HTML tags are removed (YAS too), whitespace collapsed, and the text is cut at {@link #MAX_LENGTH} characters.
 */
public final class ProductDocumentFormatter {

    static final int MAX_LENGTH = 2000;
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private ProductDocumentFormatter() {
    }

    public static String format(ProductEsDetailVm summary, ProductDetailVm detail) {
        List<String> categories = summary.categories() == null ? List.of() : summary.categories();
        String categoryText = clean(String.join(", ", categories));
        List<String> parts = new ArrayList<>();
        add(parts, categoryText);
        add(parts, summary.name());
        add(parts, summary.brand() == null ? null : "Brand: " + summary.brand());
        add(parts, categoryText.isEmpty() ? null : "Categories: " + categoryText);
        add(parts, detail.shortDescription());
        add(parts, categoryText);
        String text = String.join(". ", parts);
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH);
    }

    private static void add(List<String> parts, String value) {
        if (value != null) {
            String cleaned = clean(value);
            if (!cleaned.isEmpty()) {
                parts.add(cleaned);
            }
        }
    }

    private static String clean(String value) {
        String text = SPACE.matcher(TAG.matcher(value).replaceAll(" ")).replaceAll(" ").strip();
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}
