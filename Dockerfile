# The jar is built locally (./gradlew buildFatJar): Gradle needs more RAM than the VPS has.
# The server only builds this thin image around it.
FROM eclipse-temurin:21-jre-alpine
LABEL finance.app=bot
WORKDIR /app
COPY build/libs/finance.jar app.jar
ENV DB_PATH=/app/data/finance.db \
    JAVA_OPTS="-Xmx128m -XX:MaxMetaspaceSize=96m -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 --enable-native-access=ALL-UNNAMED"
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
