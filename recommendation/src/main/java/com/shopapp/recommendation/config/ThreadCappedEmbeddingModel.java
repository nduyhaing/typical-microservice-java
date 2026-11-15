package com.shopapp.recommendation.config;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.core.io.Resource;

/**
 * Spring AI's ONNX embedding model with a bounded number of ONNX Runtime threads (decisions.md 5-71). Spring AI 2.0.2
 * creates its session with default options: one intra-op thread per host core, which ignores the container's CPU
 * and memory limits (each thread has its own arena). After Spring AI loaded the model, the session is re-created from
 * the same cached model file with {@code intraOpThreads} intra-op threads, one inter-op thread and no spinning, and
 * swapped in (Spring AI offers no hook for session options, so its private {@code session} field is replaced).
 */
@Slf4j
class ThreadCappedEmbeddingModel extends TransformersEmbeddingModel {

    private final int intraOpThreads;

    ThreadCappedEmbeddingModel(int intraOpThreads) {
        this.intraOpThreads = intraOpThreads;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        if (intraOpThreads <= 0) {
            return;
        }
        Field sessionField = TransformersEmbeddingModel.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        Field modelField = TransformersEmbeddingModel.class.getDeclaredField("modelResource");
        modelField.setAccessible(true);
        Method cached = TransformersEmbeddingModel.class.getDeclaredMethod("getCachedResource", Resource.class);
        cached.setAccessible(true);
        Resource model = (Resource) cached.invoke(this, modelField.get(this));
        OrtSession capped = OrtEnvironment.getEnvironment().createSession(model.getContentAsByteArray(),
            sessionOptions(intraOpThreads));
        OrtSession original = (OrtSession) sessionField.get(this);
        sessionField.set(this, capped);
        original.close();
        log.info("ONNX embedding session runs with {} intra-op thread(s)", intraOpThreads);
    }

    /** Session options: {@code threads} intra-op threads, one inter-op thread, no busy-waiting between calls. */
    static OrtSession.SessionOptions sessionOptions(int threads) throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(threads);
        options.setInterOpNumThreads(1);
        options.addConfigEntry("session.intra_op.allow_spinning", "0");
        options.addConfigEntry("session.inter_op.allow_spinning", "0");
        return options;
    }
}
