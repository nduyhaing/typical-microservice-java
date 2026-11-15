FROM eclipse-temurin:27-jre
# cap the JVM so ~15 services fit in the Docker VM (default max heap is 1/4 of the VM per JVM)
ENV JAVA_TOOL_OPTIONS="-Xmx320m -Xss512k -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC"
# ~12 services share one Postgres; the default pool of 10 each exhausted max_connections
ENV SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
COPY target/location-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
