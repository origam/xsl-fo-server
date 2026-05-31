# syntax=docker/dockerfile:1
#
# Copyright 2005 - 2026 Advantage Solutions, s. r. o.
#
# This file is part of ORIGAM (http://www.origam.org).
#
# ORIGAM is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# ORIGAM is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with ORIGAM. If not, see <http://www.gnu.org/licenses/>.

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
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -Djava.util.logging.config.file=/app/config/logging.properties -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        fontconfig \
        fonts-dejavu-core \
        fonts-liberation2 \
        fonts-noto-core \
        fonts-noto-extra \
        fonts-noto-cjk \
    && fc-cache -f \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system origam \
    && useradd --system --gid origam --home-dir /app origam \
    && mkdir -p /app/config /app/work \
    && chown -R origam:origam /app

WORKDIR /app
COPY --from=build /workspace/target/xsl-fo-server-*-shaded.jar /app/xsl-fo-server.jar
COPY config/fop.xconf /app/config/fop.xconf
COPY config/logging.properties /app/config/logging.properties

USER origam
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD ["java", "-cp", "/app/xsl-fo-server.jar", "com.origam.xslfo.Healthcheck"]
ENTRYPOINT ["java", "-jar", "/app/xsl-fo-server.jar"]
