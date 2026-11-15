package com.shopapp.recommendation.repository;

import com.shopapp.recommendation.viewmodel.RelatedProductVm;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The {@code product_embedding} table (pgvector, decisions.md 5-41). Every write is conditional on the version, so a
 * slower, older read of product never replaces a newer row or tombstone.
 */
@Repository
@RequiredArgsConstructor
public class ProductEmbeddingRepository {

    private final JdbcClient jdbc;

    /** What is stored for a published, individually visible product. */
    public record EmbeddedProduct(long id, long version, String name, String slug, Double price, String brand,
                                  String thumbnailUrl, String content, float[] embedding) {
    }

    /** All rows, tombstones included. */
    public long countRows() {
        return jdbc.sql("SELECT count(*) FROM product_embedding").query(Long.class).single();
    }

    /** The stored version (of the row or of its tombstone). */
    public Optional<Long> findVersion(long id) {
        return jdbc.sql("SELECT version FROM product_embedding WHERE product_id = :id")
            .param("id", id)
            .query(Long.class)
            .optional();
    }

    /** Inserts or replaces the row unless a newer version is stored; false when it was stale. */
    public boolean upsert(EmbeddedProduct product) {
        return jdbc.sql("""
                INSERT INTO product_embedding (product_id, version, deleted, name, slug, price, brand, thumbnail_url,
                    is_published, is_visible_individually, content, embedding, updated_on)
                VALUES (:id, :version, FALSE, :name, :slug, :price, :brand, :thumbnailUrl, TRUE, TRUE, :content,
                    CAST(:embedding AS vector), now())
                ON CONFLICT (product_id) DO UPDATE SET
                    version = EXCLUDED.version, deleted = FALSE, name = EXCLUDED.name, slug = EXCLUDED.slug,
                    price = EXCLUDED.price, brand = EXCLUDED.brand, thumbnail_url = EXCLUDED.thumbnail_url,
                    is_published = TRUE, is_visible_individually = TRUE, content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding, updated_on = now()
                WHERE product_embedding.version <= EXCLUDED.version
                """)
            .param("id", product.id())
            .param("version", product.version())
            .param("name", product.name())
            .param("slug", product.slug())
            .param("price", product.price())
            .param("brand", product.brand())
            .param("thumbnailUrl", product.thumbnailUrl())
            .param("content", product.content())
            .param("embedding", toVectorLiteral(product.embedding()))
            .update() > 0;
    }

    /** Replaces the row by a tombstone unless a newer version is stored; false when it was stale. */
    public boolean tombstone(long id, long version) {
        return jdbc.sql("""
                INSERT INTO product_embedding (product_id, version, deleted, updated_on)
                VALUES (:id, :version, TRUE, now())
                ON CONFLICT (product_id) DO UPDATE SET
                    version = EXCLUDED.version, deleted = TRUE, name = NULL, slug = NULL, price = NULL, brand = NULL,
                    thumbnail_url = NULL, is_published = FALSE, is_visible_individually = FALSE, content = NULL,
                    embedding = NULL, updated_on = now()
                WHERE product_embedding.version <= EXCLUDED.version
                """)
            .param("id", id)
            .param("version", version)
            .update() > 0;
    }

    /** The embedding (pgvector text form) of a product that may be recommended; empty otherwise. */
    public Optional<String> findRecommendableEmbedding(long id) {
        return jdbc.sql("""
                SELECT embedding::text FROM product_embedding
                WHERE product_id = :id AND NOT deleted AND is_published AND is_visible_individually
                    AND embedding IS NOT NULL
                """)
            .param("id", id)
            .query(String.class)
            .optional();
    }

    /**
     * The {@code limit} nearest recommendable products by cosine distance to {@code embedding}, nearest first,
     * without {@code excludedId}.
     */
    public List<RelatedProductVm> findNearest(String embedding, long excludedId, int limit) {
        return jdbc.sql("""
                SELECT product_id, name, slug, price, brand, thumbnail_url FROM product_embedding
                WHERE product_id <> :excludedId AND NOT deleted AND is_published AND is_visible_individually
                    AND embedding IS NOT NULL
                ORDER BY embedding <=> CAST(:embedding AS vector), product_id
                LIMIT :limit
                """)
            .param("excludedId", excludedId)
            .param("embedding", embedding)
            .param("limit", limit)
            .query((rs, rowNum) -> new RelatedProductVm(rs.getLong("product_id"), rs.getString("name"),
                rs.getString("slug"), rs.getObject("price", Double.class), rs.getString("brand"),
                rs.getString("thumbnail_url")))
            .list();
    }

    /** pgvector's text form, {@code [0.1,-0.2,...]}. */
    static String toVectorLiteral(float[] vector) {
        StringBuilder text = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(vector[i]);
        }
        return text.append(']').toString();
    }
}
