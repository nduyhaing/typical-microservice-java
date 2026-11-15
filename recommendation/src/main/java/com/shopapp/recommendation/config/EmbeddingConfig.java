package com.shopapp.recommendation.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI's ONNX transformers embedding model, run in-process on the CPU (YAS: Azure OpenAI). The files are
 * downloaded on first start and loaded at start-up, so a missing model fails the start rather than every event.
 */
@Configuration
public class EmbeddingConfig {

    /** The output of the sentence-transformers ONNX export; Spring AI mean-pools it into one vector. */
    static final String MODEL_OUTPUT = "last_hidden_state";

    @Bean
    public EmbeddingModel embeddingModel(EmbeddingProperties properties) {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setModelResource(properties.modelUrl());
        model.setTokenizerResource(properties.tokenizerUrl());
        model.setResourceCacheDirectory(properties.cacheDirectory());
        model.setModelOutputName(MODEL_OUTPUT);
        return model;
    }
}
