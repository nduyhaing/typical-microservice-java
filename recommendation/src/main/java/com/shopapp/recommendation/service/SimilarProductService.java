package com.shopapp.recommendation.service;

import com.shopapp.recommendation.repository.ProductEmbeddingRepository;
import com.shopapp.recommendation.viewmodel.RelatedProductVm;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Similar products by cosine distance between embeddings (YAS RelatedProductQuery/JdbcVectorService). Only
 * published, individually visible products are stored as recommendable, so variations and hidden products never
 * appear; the product itself is excluded. A product that is not recommendable itself gets an empty list.
 */
@Service
public class SimilarProductService {

    /** Upper bound for {@code recommendation.similarity.top-k}. */
    static final int MAX_TOP_K = 20;

    private final ProductEmbeddingRepository repository;
    private final int topK;

    public SimilarProductService(ProductEmbeddingRepository repository,
                                 @Value("${recommendation.similarity.top-k:8}") int topK) {
        this.repository = repository;
        this.topK = Math.clamp(topK, 1, MAX_TOP_K);
    }

    public List<RelatedProductVm> similarTo(long productId) {
        return repository.findRecommendableEmbedding(productId)
            .map(embedding -> repository.findNearest(embedding, productId, topK))
            .orElse(List.of());
    }
}
