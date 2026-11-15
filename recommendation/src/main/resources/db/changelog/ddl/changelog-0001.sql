--liquibase formatted sql

--changeset shopapp:recommendation-0001-vector-extension
CREATE EXTENSION IF NOT EXISTS vector;

--changeset shopapp:recommendation-0001-product-embedding
-- One row per product seen on the CDC topic (decisions.md 5-41). A removed product keeps its row as a tombstone
-- (deleted = true, no embedding) so a slower, older read of product cannot bring it back: rows are only replaced
-- by a newer version (product lastModifiedOn in microseconds; tombstones use the time of the delete).
CREATE TABLE product_embedding (
    product_id              BIGINT PRIMARY KEY,
    version                 BIGINT       NOT NULL,
    deleted                 BOOLEAN      NOT NULL DEFAULT FALSE,
    name                    VARCHAR(450),
    slug                    VARCHAR(450),
    price                   DOUBLE PRECISION,
    brand                   VARCHAR(255),
    thumbnail_url           VARCHAR(2048),
    is_published            BOOLEAN      NOT NULL DEFAULT FALSE,
    is_visible_individually BOOLEAN      NOT NULL DEFAULT FALSE,
    content                 TEXT,
    embedding               VECTOR(384),
    updated_on              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- approximate nearest neighbours by cosine distance (the model's embeddings are compared by cosine)
CREATE INDEX product_embedding_embedding_hnsw ON product_embedding USING hnsw (embedding vector_cosine_ops);
