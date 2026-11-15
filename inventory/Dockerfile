FROM eclipse-temurin:27-jre
# cap the JVM so ~15 services fit in the Docker VM (default max heap is 1/4 of the VM per JVM)
ENV JAVA_TOOL_OPTIONS="-Xmx320m -Xss512k -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC"
COPY target/inventory-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
