package com.shopapp.recommendation.config;

import static org.assertj.core.api.Assertions.assertThat;

import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.core.io.Resource;

/** The ONNX session options of the thread-capped model (decisions.md 5-71). */
class ThreadCappedEmbeddingModelTest {

    @Test
    void sessionOptions_disableSpinning() throws Exception {
        try (OrtSession.SessionOptions options = ThreadCappedEmbeddingModel.sessionOptions(2)) {
            assertThat(options.getConfigEntries())
                .containsEntry("session.intra_op.allow_spinning", "0")
                .containsEntry("session.inter_op.allow_spinning", "0");
        }
    }

    @Test
    void theSpringAiMembersTheCapReplaces_stillExist() throws Exception {
        // fail here, not at start-up, when a Spring AI upgrade renames them
        assertThat(TransformersEmbeddingModel.class.getDeclaredField("session").getType())
            .isEqualTo(OrtSession.class);
        assertThat(TransformersEmbeddingModel.class.getDeclaredField("modelResource").getType())
            .isEqualTo(Resource.class);
        assertThat(TransformersEmbeddingModel.class.getDeclaredMethod("getCachedResource", Resource.class)
            .getReturnType()).isEqualTo(Resource.class);
    }
}
