# =============================================================================
# ContentOps AI - Multi-stage Dockerfile
# Stage 1: Maven build
# Stage 2: JRE runtime
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build the application JAR using Maven
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy only pom.xml first to cache dependencies
COPY pom.xml .

# Download dependencies (cached layer unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests; CI pipeline runs tests separately)
RUN mvn clean package -DskipTests -B && \
    JAR_FILE=$(ls target/*.jar | head -1) && \
    cp "$JAR_FILE" /build/app.jar

# ---------------------------------------------------------------------------
# Stage 2: Runtime image with JRE
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy

# Install curl for health checks
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /build/app.jar app.jar

# Create a non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser && \
    chown -R appuser:appuser /app
USER appuser

# Expose the application port
EXPOSE 8080

# JVM parameters optimized for container environments
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
