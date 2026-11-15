package com.shopapp.search.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.shopapp.search.model.Product;
import com.shopapp.search.model.ProductCriteria;
import com.shopapp.search.model.ProductField;
import com.shopapp.search.model.SortType;
import com.shopapp.search.viewmodel.ProductGetVm;
import com.shopapp.search.viewmodel.ProductListGetVm;
import com.shopapp.search.viewmodel.ProductNameListVm;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

/**
 * Catalog search and suggestions (YAS search ProductService), built only with the Elasticsearch client's query
 * builders: user input is always a query value, never query JSON. Only published, individually visible products are
 * returned, also in the facet counts (decisions.md 4-14, 4-15).
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    static final String FUZZINESS_ONE = "1";
    static final int FACET_SIZE = 50;
    static final int SUGGEST_SIZE = 10;
    public static final String BRANDS = "brands";
    public static final String CATEGORIES = "categories";
    public static final String ATTRIBUTES = "attributes";

    private final ElasticsearchOperations operations;

    public ProductListGetVm search(ProductCriteria criteria) {
        boolean hasKeyword = !criteria.keyword().isBlank();
        PageRequest pageable = PageRequest.of(criteria.page(), criteria.size(), sortOf(criteria.sortType(),
            hasKeyword));
        NativeQueryBuilder query = NativeQuery.builder()
            .withQuery(q -> q.bool(b -> {
                visibleOnly(b);
                if (hasKeyword) {
                    b.must(m -> m.multiMatch(mm -> mm
                        .fields(ProductField.NAME + "^3", ProductField.BRAND, ProductField.CATEGORIES)
                        .query(criteria.keyword())
                        .fuzziness(FUZZINESS_ONE)));
                }
                return b;
            }))
            .withAggregation(BRANDS, terms(ProductField.BRAND))
            .withAggregation(CATEGORIES, terms(ProductField.CATEGORIES))
            .withAggregation(ATTRIBUTES, terms(ProductField.ATTRIBUTES))
            .withPageable(pageable)
            .withTrackTotalHits(true);
        Query selection = selectionFilter(criteria);
        if (selection != null) {
            // YAS: brand/category/attribute/price narrow the hits, not the facet counts (post_filter)
            query.withFilter(selection);
        }
        SearchHits<Product> hits = operations.search(query.build(), Product.class);
        List<ProductGetVm> products = hits.getSearchHits().stream()
            .map(hit -> ProductGetVm.fromModel(hit.getContent()))
            .toList();
        long total = hits.getTotalHits();
        int totalPages = (int) ((total + criteria.size() - 1) / criteria.size());
        return new ProductListGetVm(products, criteria.page(), criteria.size(), total, totalPages,
            criteria.page() >= totalPages - 1, facets(hits));
    }

    /** Up to 10 names of visible products whose name starts with the words typed (YAS match_phrase_prefix). */
    public ProductNameListVm suggest(String keyword) {
        if (keyword.isBlank()) {
            return new ProductNameListVm(List.of());
        }
        NativeQuery query = NativeQuery.builder()
            .withQuery(q -> q.bool(b -> {
                visibleOnly(b);
                return b.must(m -> m.matchPhrasePrefix(p -> p.field(ProductField.NAME).query(keyword)));
            }))
            .withSourceFilter(new FetchSourceFilter(true, new String[] {ProductField.NAME}, null))
            .withPageable(PageRequest.of(0, SUGGEST_SIZE))
            .build();
        SearchHits<Product> hits = operations.search(query, Product.class);
        return new ProductNameListVm(hits.getSearchHits().stream()
            .map(SearchHit::getContent)
            .map(product -> new ProductNameListVm.ProductNameGetVm(product.getName()))
            .toList());
    }

    /** Defence in depth: the consumer only indexes visible products, the queries filter on it too. */
    private static void visibleOnly(BoolQuery.Builder bool) {
        bool.filter(f -> f.term(t -> t.field(ProductField.IS_PUBLISHED).value(true)));
        bool.filter(f -> f.term(t -> t.field(ProductField.IS_VISIBLE_INDIVIDUALLY).value(true)));
    }

    private static Query selectionFilter(ProductCriteria criteria) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        boolean any = anyOf(bool, ProductField.BRAND, criteria.brands());
        any |= anyOf(bool, ProductField.CATEGORIES, criteria.categories());
        any |= anyOf(bool, ProductField.ATTRIBUTES, criteria.attributes());
        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            bool.filter(f -> f.range(r -> r.number(n -> {
                n.field(ProductField.PRICE);
                if (criteria.minPrice() != null) {
                    n.gte(criteria.minPrice());
                }
                if (criteria.maxPrice() != null) {
                    n.lte(criteria.maxPrice());
                }
                return n;
            })));
            any = true;
        }
        return any ? Query.of(q -> q.bool(bool.build())) : null;
    }

    /** The field equals one of the values (case-insensitive term per value, YAS). */
    private static boolean anyOf(BoolQuery.Builder bool, String field, List<String> values) {
        if (values.isEmpty()) {
            return false;
        }
        bool.filter(f -> f.bool(inner -> {
            values.forEach(value -> inner.should(s -> s.term(t -> t.field(field)
                .value(FieldValue.of(value)).caseInsensitive(true))));
            return inner.minimumShouldMatch("1");
        }));
        return true;
    }

    private static Aggregation terms(String field) {
        return Aggregation.of(a -> a.terms(t -> t.field(field).size(FACET_SIZE)));
    }

    private static Sort sortOf(SortType sortType, boolean hasKeyword) {
        return switch (sortType) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc(ProductField.PRICE), Sort.Order.asc("id"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc(ProductField.PRICE), Sort.Order.asc("id"));
            case DEFAULT -> hasKeyword
                ? Sort.by(Sort.Order.desc("_score"), Sort.Order.desc(ProductField.CREATED_ON), Sort.Order.asc("id"))
                : Sort.by(Sort.Order.desc(ProductField.CREATED_ON), Sort.Order.asc("id"));
        };
    }

    private static Map<String, Map<String, Long>> facets(SearchHits<Product> hits) {
        Map<String, Map<String, Long>> facets = new LinkedHashMap<>();
        for (String name : List.of(BRANDS, CATEGORIES, ATTRIBUTES)) {
            facets.put(name, new LinkedHashMap<>());
        }
        if (hits.getAggregations() instanceof ElasticsearchAggregations aggregations) {
            for (ElasticsearchAggregation aggregation : aggregations.aggregations()) {
                Aggregate aggregate = aggregation.aggregation().getAggregate();
                if (aggregate.isSterms()) {
                    Map<String, Long> counts = facets.computeIfAbsent(aggregation.aggregation().getName(),
                        name -> new LinkedHashMap<>());
                    aggregate.sterms().buckets().array()
                        .forEach(bucket -> counts.put(bucket.key().stringValue(), bucket.docCount()));
                }
            }
        }
        return facets;
    }
}
