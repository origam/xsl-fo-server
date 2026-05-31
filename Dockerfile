# syntax=docker/dockerfile:1

FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package

FROM eclipse-temurin:21-jre-jammy

ENV APP_PORT=8080 \
    FOP_BASE_URI=file:/app/work/ \
    FOP_CONFIG_FILE=/app/config/fop.xconf \
    MAX_REQUEST_BYTES=20971520 \
    RENDER_TIMEOUT_SECONDS=60 \
    WARMUP_ENABLED=true \
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system origam \
    && useradd --system --gid origam --home-dir /app origam \
    && mkdir -p /app/config /app/work \
    && chown -R origam:origam /app

WORKDIR /app
COPY --from=build /workspace/target/xsl-fo-server-*-shaded.jar /app/xsl-fo-server.jar
COPY config/fop.xconf /app/config/fop.xconf

USER origam
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD ["java", "-cp", "/app/xsl-fo-server.jar", "com.origam.xslfo.Healthcheck"]
ENTRYPOINT ["java", "-jar", "/app/xsl-fo-server.jar"]
