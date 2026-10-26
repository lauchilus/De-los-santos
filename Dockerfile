# syntax=docker/dockerfile:1
FROM node:22-bookworm-slim AS frontend
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /frontend
# Version exacta del frontend publico: actualizar este SHA para publicar otra UI.
ARG FRONTEND_REF=a58a5188e448358675e19bd5b56a46e943d72dbf
RUN git init && git remote add origin https://github.com/lauchilus/agua-frontend.git \
    && git fetch --depth 1 origin "$FRONTEND_REF" && git checkout --detach FETCH_HEAD
RUN npm ci && npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
COPY --from=frontend /frontend/dist/ ./src/main/resources/static/
# Los tests de integracion requieren una base aislada; no se ejecutan contra Neon.
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system agua && useradd --system --gid agua agua
COPY --from=backend --chown=agua:agua /build/target/agua-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER agua
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
