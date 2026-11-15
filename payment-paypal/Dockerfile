FROM eclipse-temurin:27-jre
# cap the JVM so ~15 services fit in the Docker VM (default max heap is 1/4 of the VM per JVM); no database here,
# so no Hikari pool line (decisions.md 5-20)
ENV JAVA_TOOL_OPTIONS="-Xmx192m -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC"
COPY target/payment-paypal-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
