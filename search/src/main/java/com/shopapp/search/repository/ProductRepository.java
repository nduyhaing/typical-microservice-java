package com.shopapp.search.repository;

import com.shopapp.search.model.Product;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** Creates the {@code product} index with its settings and mapping at start-up (YAS ProductRepository). */
public interface ProductRepository extends ElasticsearchRepository<Product, Long> {
}
