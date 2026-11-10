# ==============================================================================
# SupportFlow AI Backend — Multi-Stage Containerfile / Dockerfile (Java 25 LTS)
# Compatible with both Podman and Docker.
# ==============================================================================

# Stage 1: Build executable JAR using Gradle and JDK 25
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar -x test -x integrationTest --no-daemon

# Stage 2: Minimal non-root Java 25 JRE runtime image
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S supportflow && adduser -S supportflow -G supportflow

COPY --from=builder --chown=supportflow:supportflow /workspace/build/libs/*.jar /app/app.jar

USER supportflow

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
