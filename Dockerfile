# syntax=docker/dockerfile:1

# ---- Stage 1: dependencies (cached apart from source) ----
# Copia solo lo necesario para resolver dependencias. Mientras build.gradle,
# settings.gradle y el wrapper no cambien, Docker reusa esta capa entera
# aunque el codigo fuente cambie constantemente.
FROM eclipse-temurin:21-jdk-alpine AS deps
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && \
    ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# ---- Stage 2: build (compila con las dependencias ya cacheadas) ----
FROM deps AS build
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Stage 3: runtime (solo el jar, sin JDK/Gradle) ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
