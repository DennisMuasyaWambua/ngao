# syntax=docker/dockerfile:1

# =============================================================================
# core-payment-engine — multi-stage image
#   stage 1 (build):   compile + package the Spring Boot fat jar with Maven
#   stage 2 (runtime): run that jar on a slim JRE as a non-root user
# =============================================================================

# ---- Build stage ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first so this layer stays cached while only sources change.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

# Compile and package the executable (fat) jar.
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Run as an unprivileged user instead of root.
RUN groupadd --system spring && useradd --system --gid spring spring

# Exactly one *.jar is produced by the build stage (the *.jar.original is excluded).
COPY --from=build --chown=spring:spring /workspace/target/*.jar app.jar

USER spring:spring

# Documentation only; the real port comes from application.yml / SERVER_PORT.
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
