FROM eclipse-temurin:27-jre
# cap the JVM so ~15 services fit in the Docker VM (default max heap is 1/4 of the VM per JVM). The ONNX model
# (~90 MB) is read into the heap once at start-up; inference memory is native (ONNX Runtime), outside -Xmx
# (decisions.md 5-44). Native access for ONNX Runtime and the DJL tokenizer (JNI) is enabled explicitly.
ENV JAVA_TOOL_OPTIONS="-Xmx320m -Xss512k -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20"
# ~12 services share one Postgres; the default pool of 10 each exhausted max_connections
ENV SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
# model files are cached here (a named volume in compose); DJL must not phone home or download engines
ENV RECOMMENDATION_MODEL_CACHE=/var/cache/shopapp/onnx-model \
    DJL_OFFLINE=true \
    OPT_OUT_TRACKING=true
COPY target/recommendation-*.jar app.jar
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app.jar"]
