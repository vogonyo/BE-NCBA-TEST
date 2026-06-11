# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies layer separately for faster rebuilds
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security (OWASP container hardening)
RUN addgroup -S rdas && adduser -S rdas -G rdas
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar
RUN chown rdas:rdas app.jar

USER rdas

EXPOSE 8080

# Tune JVM for container memory limits
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
