# ============================================================================
# Stage 1: Build
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# ============================================================================
# Stage 2: Runtime
# ============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache \
    libreoffice \
    ttf-dejavu \
    font-noto \
    su-exec \
    && rm -rf /var/cache/apk/*

RUN soffice --version || echo "⚠️ LibreOffice non installé correctement"

# Créer un utilisateur non-root avec UID/GID explicites
RUN addgroup -g 1001 -S spring && adduser -u 1001 -S spring -G spring

# Copier le JAR
COPY --from=build --chown=spring:spring /app/target/*.jar app.jar

# Créer les dossiers et permissions
RUN mkdir -p /app/uploads/extracted-images \
    && mkdir -p /app/logs \
    && mkdir -p /tmp/libreoffice \
    && chown -R spring:spring /app \
    && chown -R spring:spring /tmp/libreoffice \
    && chmod -R 755 /app/uploads

# Entrypoint — tourne en ROOT pour pouvoir chown, puis drop vers spring via su-exec
RUN printf '#!/bin/sh\n\
set -e\n\
echo "🚀 Démarrage..."\n\
mkdir -p /app/uploads/extracted-images\n\
mkdir -p /app/logs\n\
mkdir -p /tmp/libreoffice\n\
chown -R spring:spring /app/uploads\n\
chown -R spring:spring /app/logs\n\
chown -R spring:spring /tmp/libreoffice\n\
chmod -R 755 /app/uploads\n\
echo "✅ Permissions OK"\n\
exec su-exec spring java $JAVA_OPTS -jar /app/app.jar\n' > /entrypoint.sh \
    && chmod +x /entrypoint.sh

# ❌ PAS de USER spring ici — l entrypoint tourne en root puis drop via su-exec
EXPOSE 8090

ENV JAVA_OPTS="-Xms512m -Xmx2048m"
ENV LIBREOFFICE_PATH="/usr/bin/soffice"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["/entrypoint.sh"]