package com.shopapp.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The local embedding model (decisions.md 5-40).
 *
 * @param modelUrl       the ONNX model file (all-MiniLM-L6-v2, ~90 MB)
 * @param tokenizerUrl   its tokenizer.json (~0.5 MB)
 * @param cacheDirectory where both are kept after the first download (a volume in compose)
 * @param intraOpThreads ONNX Runtime intra-op threads (5-71); null or 0 = ONNX Runtime's default (one per host core)
 */
@ConfigurationProperties(prefix = "recommendation.embedding")
public record EmbeddingProperties(String modelUrl, String tokenizerUrl, String cacheDirectory,
                                  Integer intraOpThreads) {
}
