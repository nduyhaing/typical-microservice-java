package com.shopapp.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.support.AbstractStorefrontIT;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Back office product endpoints (YAS paths and view models): list, detail, create (incl. options and variations),
 * update, soft delete, product options; changes show up on the storefront.
 */
class ProductAdminIT extends AbstractStorefrontIT {

    @Autowired
    private JdbcTemplate jdbc;

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** A simple product body (YAS ProductPostVm/ProductPutVm shape) without variations. */
    private static String simple(String name, String slug, String sku, double price, Long brandId,
                                 String categoryIds) {
        return "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\",\"sku\":\"" + sku + "\",\"gtin\":\"\","
            + "\"price\":" + price + ",\"brandId\":" + brandId + ",\"categoryIds\":" + categoryIds + ","
            + "\"shortDescription\":\"Short\",\"description\":\"Long description\",\"specification\":\"Spec\","
            + "\"isAllowedToOrder\":true,\"isPublished\":true,\"isFeatured\":false,\"isVisibleIndividually\":true,"
            + "\"stockTrackingEnabled\":false,\"length\":10,\"width\":5,\"height\":1,\"weight\":0.2,"
            + "\"dimensionUnit\":\"CM\",\"metaTitle\":\"t\",\"metaKeyword\":\"k\",\"metaDescription\":\"m\","
            + "\"variations\":[],\"productOptionValues\":[],\"productImageIds\":[],\"relatedProductIds\":[]}";
    }

    private static long idOf(String variationsJson, String slug) {
        List<Number> ids = JsonPath.read(variationsJson, "$[?(@.slug == '" + slug + "')].id");
        return ids.get(0).longValue();
    }

    private long createOption(String name) throws Exception {
        String response = mockMvc.perform(json(post("/backoffice/product-options"), "{\"name\":\"" + name + "\"}")
                .with(admin()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    /** Galaxy S24 with option Color: Black 799 and Violet 819. Returns the created parent id. */
    private long createGalaxyWithVariations(long colorId) throws Exception {
        String body = "{\"name\":\"Galaxy S24\",\"slug\":\"galaxy-s24\",\"sku\":\"GS24\",\"price\":799,"
            + "\"isAllowedToOrder\":true,\"isPublished\":true,\"isVisibleIndividually\":true,"
            + "\"categoryIds\":[],"
            + "\"variations\":["
            + "{\"name\":\"Galaxy S24 Black\",\"slug\":\"galaxy-s24-black\",\"sku\":\"GS24-B\",\"price\":799,"
            + "\"optionValuesByOptionId\":{\"" + colorId + "\":\"Black\"}},"
            + "{\"name\":\"Galaxy S24 Violet\",\"slug\":\"galaxy-s24-violet\",\"sku\":\"GS24-V\",\"price\":819,"
            + "\"optionValuesByOptionId\":{\"" + colorId + "\":\"Violet\"}}],"
            + "\"productOptionValues\":[{\"productOptionId\":" + colorId + ",\"displayOrder\":1,"
            + "\"value\":[\"Black\",\"Violet\"]}]}";
        String response = mockMvc.perform(json(post("/backoffice/products"), body).with(admin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("galaxy-s24"))
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Test
    void listProducts_filtersByNameAndBrand_andPages() throws Exception {
        Brand apple = fixtures.brand("Apple", "apple");
        fixtures.ofBrand(fixtures.product("iPhone 15", "iphone-15", 999), apple);
        fixtures.ofBrand(fixtures.product("iPhone 16", "iphone-16", 1099), apple);
        fixtures.product("Phone case", "phone-case", 19);
        Product draft = fixtures.product("iPhone 17 draft", "iphone-17", 1199);
        draft.setPublished(false);
        fixtures.save(draft);
        Product parent = fixtures.product("Galaxy", "galaxy", 799);
        fixtures.variation(parent, "Galaxy Black", "galaxy-black", 799);

        mockMvc.perform(get("/backoffice/products").param("product-name", "IPHONE").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productContent[*].slug")
                .value(containsInAnyOrder("iphone-15", "iphone-16", "iphone-17")))
            .andExpect(jsonPath("$.productContent[?(@.slug == 'iphone-17')].isPublished").value(false))
            // the row's own version, used by recommendation's reindex (decisions.md 5-70)
            .andExpect(jsonPath("$.productContent[0].lastModifiedOn").isNotEmpty())
            .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/backoffice/products").param("brand-name", "Apple").param("pageSize", "1")
                .param("pageNo", "1").with(admin()))
            .andExpect(jsonPath("$.productContent", hasSize(1)))
            .andExpect(jsonPath("$.productContent[0].slug").value("iphone-16"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2));
        // Variations are managed through their parent and are not listed (products without a brand are).
        mockMvc.perform(get("/backoffice/products").param("product-name", "galaxy").with(admin()))
            .andExpect(jsonPath("$.productContent[*].slug").value(containsInAnyOrder("galaxy")));
    }

    @Test
    void listProducts_badPaging_returns400() throws Exception {
        mockMvc.perform(get("/backoffice/products").param("pageNo", "-1").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/products").param("pageSize", "0").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/products").param("pageSize", "101").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/products").param("pageNo", "x").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createSimpleProduct_isOnStorefront_andInAdminDetail() throws Exception {
        Brand apple = fixtures.brand("Apple", "apple");
        Category phones = fixtures.category("Phones", "phones");

        String response = mockMvc.perform(json(post("/backoffice/products"),
                simple("iPhone 15", "iPhone-15", "IP15", 999, apple.getId(), "[" + phones.getId() + "]"))
                .with(admin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("iPhone 15"))
            .andExpect(jsonPath("$.slug").value("iphone-15"))
            .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(response, "$.id")).longValue();

        mockMvc.perform(get("/storefront/product/iphone-15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(999.0))
            .andExpect(jsonPath("$.brandName").value("Apple"))
            .andExpect(jsonPath("$.productCategories").value(containsInAnyOrder("Phones")))
            .andExpect(jsonPath("$.shortDescription").value("Short"))
            .andExpect(jsonPath("$.description").value("Long description"));
        mockMvc.perform(get("/storefront/category/phones/products"))
            .andExpect(jsonPath("$.productContent[0].slug").value("iphone-15"));

        mockMvc.perform(get("/backoffice/products/{id}", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku").value("IP15"))
            .andExpect(jsonPath("$.brandId").value(apple.getId()))
            .andExpect(jsonPath("$.categories[0].id").value(phones.getId()))
            .andExpect(jsonPath("$.categories[0].name").value("Phones"))
            .andExpect(jsonPath("$.metaDescription").value("m"))
            .andExpect(jsonPath("$.isVisible").value(true))
            .andExpect(jsonPath("$.dimensionUnit").value("CM"));
    }

    @Test
    void createProduct_withoutBrandOrCategories_returns201() throws Exception {
        mockMvc.perform(json(post("/backoffice/products"),
                "{\"name\":\"Cable\",\"slug\":\"cable\",\"price\":9.5,\"isPublished\":true,"
                    + "\"isVisibleIndividually\":true}").with(admin()))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/storefront/product/cable")).andExpect(status().isOk());
    }

    @Test
    void createProduct_invalidInput_returns400() throws Exception {
        mockMvc.perform(json(post("/backoffice/products"), simple("A", "a", "A1", -1, null, "[]")).with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/backoffice/products"), "{\"name\":\"A\",\"slug\":\"a\"}").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/backoffice/products"), "{\"name\":\"\",\"slug\":\"a\",\"price\":1}")
                .with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/backoffice/products"),
                simple("A", "a", "A1", 1, 999L, "[]")).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Brand 999 is not found"));
        mockMvc.perform(json(post("/backoffice/products"),
                simple("A", "a", "A1", 1, null, "[999]")).with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/backoffice/products"),
                simple("A", "a", "A1", 1, null, "[]").replace("\"length\":10", "\"length\":2")).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Please make sure length greater than width"));
        mockMvc.perform(get("/backoffice/products").with(admin()))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void createProduct_duplicateSlugOrSku_returns400() throws Exception {
        Product existing = fixtures.product("iPhone 15", "iphone-15", 999);
        existing.setSku("IP15");
        existing.setPublished(false);
        fixtures.save(existing);

        mockMvc.perform(json(post("/backoffice/products"), simple("Other", "IPHONE-15", "X1", 1, null, "[]"))
                .with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Slug iphone-15 is already existed or is duplicated"));
        mockMvc.perform(json(post("/backoffice/products"), simple("Other", "other", "IP15", 1, null, "[]"))
                .with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Sku IP15 is already existed or is duplicated"));
    }

    @Test
    void createProduct_productsWithoutSku_doNotClash() throws Exception {
        mockMvc.perform(json(post("/backoffice/products"), "{\"name\":\"A\",\"slug\":\"a\",\"price\":1}")
                .with(admin()))
            .andExpect(status().isCreated());
        mockMvc.perform(json(post("/backoffice/products"), "{\"name\":\"B\",\"slug\":\"b\",\"price\":1}")
                .with(admin()))
            .andExpect(status().isCreated());
    }

    @Test
    void createProductWithVariations_storefrontShowsOptionsAndPrices() throws Exception {
        long colorId = createOption("Color");
        long id = createGalaxyWithVariations(colorId);

        mockMvc.perform(get("/storefront/product/galaxy-s24"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasOptions").value(true));
        mockMvc.perform(get("/storefront/product-variations/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[?(@.slug == 'galaxy-s24-violet')].price").value(819.0))
            .andExpect(jsonPath("$[?(@.slug == 'galaxy-s24-black')].options." + colorId).value("Black"));
        mockMvc.perform(get("/storefront/product-option-combinations/{id}/values", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].productOptionValue").value(containsInAnyOrder("Black", "Violet")))
            .andExpect(jsonPath("$[0].productOptionName").value("Color"));
        // Variations are not listed on their own and have no page of their own (2a-8, 2a-15).
        mockMvc.perform(get("/storefront/products").param("pageSize", "10"))
            .andExpect(jsonPath("$.productContent[*].slug").value(containsInAnyOrder("galaxy-s24")));
        mockMvc.perform(get("/storefront/product/galaxy-s24-black")).andExpect(status().isNotFound());

        mockMvc.perform(get("/backoffice/product-variations/{id}", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[*].sku").value(containsInAnyOrder("GS24-B", "GS24-V")));
    }

    @Test
    void createdVariation_canBeOrdered_whileParentPublished() throws Exception {
        long colorId = createOption("Color");
        long id = createGalaxyWithVariations(colorId);
        String variationsJson = mockMvc.perform(get("/backoffice/product-variations/{id}", id).with(admin()))
            .andReturn().getResponse().getContentAsString();
        long blackId = idOf(variationsJson, "galaxy-s24-black");

        mockMvc.perform(get("/products").param("ids", String.valueOf(blackId)).with(jwt()))
            .andExpect(jsonPath("$.productCheckoutListVms", hasSize(1)));

        mockMvc.perform(delete("/backoffice/products/{id}", id).with(admin()))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/products").param("ids", String.valueOf(blackId)).with(jwt()))
            .andExpect(jsonPath("$.productCheckoutListVms", hasSize(0)));
        mockMvc.perform(get("/storefront/product-variations/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void createProductWithVariations_duplicateSlugOrSkuAcrossVariations_returns400() throws Exception {
        long colorId = createOption("Color");
        String twoBlack = "{\"name\":\"P\",\"slug\":\"p\",\"sku\":\"P\",\"price\":1,"
            + "\"variations\":["
            + "{\"name\":\"P Black\",\"slug\":\"p-black\",\"sku\":\"P-B\",\"price\":1,"
            + "\"optionValuesByOptionId\":{\"" + colorId + "\":\"Black\"}},"
            + "{\"name\":\"P Violet\",\"slug\":\"P-BLACK\",\"sku\":\"P-V\",\"price\":1,"
            + "\"optionValuesByOptionId\":{\"" + colorId + "\":\"Violet\"}}],"
            + "\"productOptionValues\":[{\"productOptionId\":" + colorId + ",\"value\":[\"Black\",\"Violet\"]}]}";

        mockMvc.perform(json(post("/backoffice/products"), twoBlack).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Slug P-BLACK is already existed or is duplicated"));
        mockMvc.perform(json(post("/backoffice/products"), twoBlack.replace("P-BLACK", "p-violet")
                .replace("\"sku\":\"P-V\"", "\"sku\":\"P\"")).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Sku P is already existed or is duplicated"));
        mockMvc.perform(get("/backoffice/products").with(admin()))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void createProductWithVariations_unknownOptionOrValue_returns400() throws Exception {
        long colorId = createOption("Color");
        String body = "{\"name\":\"P\",\"slug\":\"p\",\"price\":1,"
            + "\"variations\":[{\"name\":\"P Red\",\"slug\":\"p-red\",\"price\":1,"
            + "\"optionValuesByOptionId\":{\"" + colorId + "\":\"Red\"}}],"
            + "\"productOptionValues\":[{\"productOptionId\":" + colorId + ",\"value\":[\"Black\"]}]}";

        mockMvc.perform(json(post("/backoffice/products"), body).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Product option value Red is not found"));
        mockMvc.perform(json(post("/backoffice/products"), body.replace("\"productOptionId\":" + colorId,
                "\"productOptionId\":999")).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("The provided product options could not be found"));
        mockMvc.perform(json(post("/backoffice/products"), body.replace(",\"productOptionValues\":[{\"productOptionId\":"
                + colorId + ",\"value\":[\"Black\"]}]", "")).with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateProduct_changesPrice_onStorefront_andKeepsCategories() throws Exception {
        Category phones = fixtures.category("Phones", "phones");
        Brand apple = fixtures.brand("Apple", "apple");
        Product iphone = fixtures.product("iPhone 15", "iphone-15", 999);
        iphone.setSku("IP15");
        fixtures.save(iphone);
        fixtures.inCategory(iphone, phones);

        mockMvc.perform(json(put("/backoffice/products/{id}", iphone.getId()),
                simple("iPhone 15", "iphone-15", "IP15", 899, apple.getId(), "[" + phones.getId() + "]"))
                .with(admin()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/storefront/product/iphone-15"))
            .andExpect(jsonPath("$.price").value(899.0))
            .andExpect(jsonPath("$.brandName").value("Apple"))
            .andExpect(jsonPath("$.productCategories").value(containsInAnyOrder("Phones")));
        mockMvc.perform(get("/storefront/products").param("productName", "iphone"))
            .andExpect(jsonPath("$.productContent[0].price").value(899.0));
    }

    @Test
    void updateProduct_changingOnlyCategories_touchesTheProductRow() throws Exception {
        // Debezium captures the product row only: a categories-only edit must still update it, or search never
        // hears of it (decisions.md 4-73)
        Category phones = fixtures.category("Phones", "phones");
        Category tablets = fixtures.category("Tablets", "tablets");
        Product iphone = fixtures.product("iPhone 15", "iphone-15", 999);
        iphone.setSku("IP15");
        fixtures.save(iphone);
        fixtures.inCategory(iphone, phones);
        String body = simple("iPhone 15", "iphone-15", "IP15", 999, null, "[" + phones.getId() + "]");
        mockMvc.perform(json(put("/backoffice/products/{id}", iphone.getId()), body).with(admin()))
            .andExpect(status().isNoContent());
        Timestamp before = lastModifiedOn(iphone.getId());

        Thread.sleep(5);
        mockMvc.perform(json(put("/backoffice/products/{id}", iphone.getId()),
                simple("iPhone 15", "iphone-15", "IP15", 999, null, "[" + tablets.getId() + "]")).with(admin()))
            .andExpect(status().isNoContent());

        assertThat(lastModifiedOn(iphone.getId())).isAfter(before);
        mockMvc.perform(get("/storefront/products-es/{id}", iphone.getId()))
            .andExpect(jsonPath("$.categories").value(containsInAnyOrder("Tablets")));
    }

    private Timestamp lastModifiedOn(long id) {
        return jdbc.queryForObject("select last_modified_on from product where id = ?", Timestamp.class, id);
    }

    @Test
    void updateProduct_unpublish_hidesFromStorefront_slugOfOtherRefused() throws Exception {
        fixtures.product("Other", "other", 1);
        Product iphone = fixtures.product("iPhone 15", "iphone-15", 999);

        mockMvc.perform(json(put("/backoffice/products/{id}", iphone.getId()),
                simple("iPhone 15", "other", "IP15", 999, null, "[]")).with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(put("/backoffice/products/{id}", iphone.getId()),
                simple("iPhone 15", "iphone-15", "IP15", 999, null, "[]")
                    .replace("\"isPublished\":true", "\"isPublished\":false")).with(admin()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/storefront/product/iphone-15")).andExpect(status().isNotFound());
        mockMvc.perform(get("/storefront/products").param("productName", "iphone"))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void updateProduct_addsVariation_andUpdatesExistingVariationPrice() throws Exception {
        long colorId = createOption("Color");
        long id = createGalaxyWithVariations(colorId);
        String variationsJson = mockMvc.perform(get("/backoffice/product-variations/{id}", id).with(admin()))
            .andReturn().getResponse().getContentAsString();
        long blackId = idOf(variationsJson, "galaxy-s24-black");
        long violetId = idOf(variationsJson, "galaxy-s24-violet");

        String body = "{\"name\":\"Galaxy S24\",\"slug\":\"galaxy-s24\",\"sku\":\"GS24\",\"price\":799,"
            + "\"isAllowedToOrder\":true,\"isPublished\":true,\"isVisibleIndividually\":true,\"categoryIds\":[],"
            + "\"variations\":["
            + "{\"id\":" + blackId + ",\"name\":\"Galaxy S24 Black\",\"slug\":\"galaxy-s24-black\",\"sku\":\"GS24-B\","
            + "\"price\":749,\"optionValuesByOptionId\":{\"" + colorId + "\":\"Black\"}},"
            + "{\"id\":" + violetId + ",\"name\":\"Galaxy S24 Violet\",\"slug\":\"galaxy-s24-violet\","
            + "\"sku\":\"GS24-V\",\"price\":819,\"optionValuesByOptionId\":{\"" + colorId + "\":\"Violet\"}},"
            + "{\"name\":\"Galaxy S24 Amber\",\"slug\":\"galaxy-s24-amber\",\"sku\":\"GS24-A\",\"price\":829,"
            + "\"optionValuesByOptionId\":{\"" + colorId + "\":\"Amber\"}}],"
            + "\"productOptionValues\":[{\"productOptionId\":" + colorId + ",\"displayOrder\":1,"
            + "\"value\":[\"Black\",\"Violet\",\"Amber\"]}]}";
        mockMvc.perform(json(put("/backoffice/products/{id}", id), body).with(admin()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/storefront/product-variations/{id}", id))
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[?(@.slug == 'galaxy-s24-black')].price").value(749.0))
            .andExpect(jsonPath("$[?(@.slug == 'galaxy-s24-amber')].price").value(829.0));
        mockMvc.perform(get("/storefront/product-option-combinations/{id}/values", id))
            .andExpect(jsonPath("$[*].productOptionValue").value(containsInAnyOrder("Black", "Violet", "Amber")));
    }

    /** Shirt with option Color and the given variations (id or new) as an update/create body. */
    private static String shirt(long colorId, String variations, String values) {
        return "{\"name\":\"Shirt\",\"slug\":\"shirt\",\"sku\":\"SH\",\"price\":20,"
            + "\"isAllowedToOrder\":true,\"isPublished\":true,\"isVisibleIndividually\":true,\"categoryIds\":[],"
            + "\"variations\":[" + variations + "],"
            + "\"productOptionValues\":[{\"productOptionId\":" + colorId + ",\"displayOrder\":1,"
            + "\"value\":[" + values + "]}]}";
    }

    private static String shirtVariation(Long id, long colorId, String color) {
        return "{" + (id == null ? "" : "\"id\":" + id + ",") + "\"name\":\"Shirt " + color + "\","
            + "\"slug\":\"shirt-" + color.toLowerCase(Locale.ROOT) + "\",\"sku\":\"SH-" + color + "\",\"price\":20,"
            + "\"optionValuesByOptionId\":{\"" + colorId + "\":\"" + color + "\"}}";
    }

    @Test
    void updateProduct_variationLeftOut_isDeleted() throws Exception {
        long colorId = createOption("Color");
        String created = mockMvc.perform(json(post("/backoffice/products"), shirt(colorId,
                shirtVariation(null, colorId, "Red") + "," + shirtVariation(null, colorId, "Blue"),
                "\"Red\",\"Blue\"")).with(admin()))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        String variationsJson = mockMvc.perform(get("/backoffice/product-variations/{id}", id).with(admin()))
            .andReturn().getResponse().getContentAsString();
        long redId = idOf(variationsJson, "shirt-red");
        long blueId = idOf(variationsJson, "shirt-blue");

        mockMvc.perform(json(put("/backoffice/products/{id}", id),
                shirt(colorId, shirtVariation(redId, colorId, "Red"), "\"Red\"")).with(admin()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/storefront/product-variations/{id}", id))
            .andExpect(jsonPath("$[*].slug").value(containsInAnyOrder("shirt-red")));
        mockMvc.perform(get("/storefront/product-option-combinations/{id}/values", id))
            .andExpect(jsonPath("$[*].productOptionValue").value(containsInAnyOrder("Red")));
        mockMvc.perform(get("/backoffice/product-variations/{id}", id).with(admin()))
            .andExpect(jsonPath("$[*].slug").value(containsInAnyOrder("shirt-red")));
        // the removed variation can no longer be ordered
        mockMvc.perform(get("/products").param("ids", String.valueOf(blueId)).with(jwt()))
            .andExpect(jsonPath("$.productCheckoutListVms", hasSize(0)));
    }

    @Test
    void variation_cannotBeEditedOrReadDirectly_returns400() throws Exception {
        long colorId = createOption("Color");
        long id = createGalaxyWithVariations(colorId);
        String variationsJson = mockMvc.perform(get("/backoffice/product-variations/{id}", id).with(admin()))
            .andReturn().getResponse().getContentAsString();
        long blackId = idOf(variationsJson, "galaxy-s24-black");

        mockMvc.perform(get("/backoffice/products/{id}", blackId).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Product " + blackId
                + " is a variation; edit its parent product instead"));
        mockMvc.perform(json(put("/backoffice/products/{id}", blackId),
                simple("Galaxy S24 Black", "galaxy-s24-black", "GS24-B", 1, null, "[]")).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Product " + blackId
                + " is a variation; edit its parent product instead"));
        // unchanged: still hidden from lists and priced as before
        mockMvc.perform(get("/storefront/product-variations/{id}", id))
            .andExpect(jsonPath("$[?(@.slug == 'galaxy-s24-black')].price").value(799.0));
        mockMvc.perform(get("/storefront/product/galaxy-s24-black")).andExpect(status().isNotFound());
    }

    @Test
    void updateProduct_unknown_returns404() throws Exception {
        mockMvc.perform(json(put("/backoffice/products/{id}", 999), simple("A", "a", "A", 1, null, "[]"))
                .with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteProduct_hidesFromStorefront_keepsInAdmin() throws Exception {
        Product iphone = fixtures.product("iPhone 15", "iphone-15", 999);

        mockMvc.perform(delete("/backoffice/products/{id}", iphone.getId()).with(admin()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/storefront/product/iphone-15")).andExpect(status().isNotFound());
        mockMvc.perform(get("/backoffice/products/{id}", iphone.getId()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isPublished").value(false));
        mockMvc.perform(delete("/backoffice/products/{id}", 999).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteVariation_removesItFromStorefrontVariations() throws Exception {
        long colorId = createOption("Color");
        long id = createGalaxyWithVariations(colorId);
        String variationsJson = mockMvc.perform(get("/backoffice/product-variations/{id}", id).with(admin()))
            .andReturn().getResponse().getContentAsString();
        long blackId = idOf(variationsJson, "galaxy-s24-black");

        mockMvc.perform(delete("/backoffice/products/{id}", blackId).with(admin()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/storefront/product-variations/{id}", id))
            .andExpect(jsonPath("$[*].slug").value(containsInAnyOrder("galaxy-s24-violet")));
        mockMvc.perform(get("/storefront/product-option-combinations/{id}/values", id))
            .andExpect(jsonPath("$[*].productOptionValue").value(containsInAnyOrder("Violet")));
        mockMvc.perform(get("/backoffice/product-variations/{id}", id).with(admin()))
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void productOptions_listAndCreate_duplicateRefused() throws Exception {
        createOption("Color");
        createOption("Size");

        mockMvc.perform(get("/backoffice/product-options").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("Color", "Size")));
        mockMvc.perform(json(post("/backoffice/product-options"), "{\"name\":\"Color\"}").with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Request name Color is already existed"));
        mockMvc.perform(json(post("/backoffice/product-options"), "{\"name\":\"\"}").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void customer_isForbidden() throws Exception {
        Product iphone = fixtures.product("iPhone 15", "iphone-15", 999);

        mockMvc.perform(get("/backoffice/products").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/products/{id}", iphone.getId()).with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(json(post("/backoffice/products"), simple("A", "a", "A", 1, null, "[]")).with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/backoffice/products/{id}", iphone.getId()),
                simple("A", "a", "A", 1, null, "[]")).with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/backoffice/products/{id}", iphone.getId()).with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/product-variations/{id}", iphone.getId()).with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/product-options").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/storefront/product/iphone-15")).andExpect(jsonPath("$.price").value(999.0));
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/backoffice/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/backoffice/product-options")).andExpect(status().isUnauthorized());
    }

    @Test
    void relatedProducts_backoffice_includeUnpublished() throws Exception {
        Product draft = fixtures.product("Draft phone", "draft-phone", 100);
        draft.setPublished(false);
        fixtures.save(draft);
        Product cable = fixtures.product("Cable", "cable", 10);
        Product hiddenCase = fixtures.product("Hidden case", "hidden-case", 5);
        hiddenCase.setPublished(false);
        fixtures.save(hiddenCase);
        fixtures.related(draft, cable);
        fixtures.related(draft, hiddenCase);

        mockMvc.perform(get("/backoffice/products/related-products/{id}", draft.getId()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].slug").value(org.hamcrest.Matchers.containsInAnyOrder("cable", "hidden-case")));
        mockMvc.perform(get("/backoffice/products/related-products/{id}", draft.getId()).with(customer()))
            .andExpect(status().isForbidden());
    }
}

