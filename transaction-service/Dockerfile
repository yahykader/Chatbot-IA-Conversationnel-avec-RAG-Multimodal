# ==============================================================================
# DOCKERFILE - TRANSACTION SERVICE (Spring Boot + Maven)
# ==============================================================================
# 
# Build multi-stage pour optimiser la taille et la sécurité
# ==============================================================================

# ------------------------------------------------------------------------------
# STAGE 1: BUILD avec Maven
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

# Informations de build
LABEL stage=builder
LABEL application=transaction-service

WORKDIR /build

# Copier pom.xml et télécharger dépendances (optimisation cache Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copier le code source
COPY src ./src

# Builder l'application (skip tests pour accélérer)
RUN mvn clean package -DskipTests -B

# Vérifier que le JAR a été créé
RUN ls -lh target/*.jar

# ------------------------------------------------------------------------------
# STAGE 2: RUNTIME - Image minimale
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Métadonnées
LABEL maintainer="your-email@example.com"
LABEL application="transaction-service"
LABEL version="1.0.0"

WORKDIR /app

# Installer outils système nécessaires
RUN apk add --no-cache \
    curl \
    netcat-openbsd \
    bash \
    tzdata

# Créer utilisateur non-root pour sécurité
RUN addgroup -g 1000 -S spring && \
    adduser -u 1000 -S spring -G spring

# Copier le JAR depuis le stage de build
COPY --from=build /build/target/*.jar app.jar

# Créer répertoires nécessaires avec bonnes permissions
RUN mkdir -p /app/uploads /app/logs /app/temp && \
    chown -R spring:spring /app

# Changer vers utilisateur non-root
USER spring:spring

# Exposer le port de l'application
EXPOSE 8090

# Variables d'environnement par défaut
ENV JAVA_OPTS="-Xmx2g -Xms512m" \
    SPRING_PROFILES_ACTIVE="docker" \
    TZ="Europe/Paris"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8090/actuator/health || exit 1

# Point d'entrée avec variables d'environnement
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
