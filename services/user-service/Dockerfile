FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY services ./services
RUN chmod +x gradlew \
    && ./gradlew --no-daemon :services:user-service:bootJar \
    && find services/user-service/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/app.jar \;

FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S spring \
    && adduser -S -G spring -u 10001 spring

WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/app.jar app.jar
USER 10001
EXPOSE 8082
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=3 \
    CMD curl --fail --silent http://localhost:8082/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
