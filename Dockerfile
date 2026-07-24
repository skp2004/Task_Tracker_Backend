# ─────────────────────────────────────────────────────────────────
# Stage 1 — BUILD: compile the JAR using Maven + JDK 21
# ─────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy dependency descriptors first — leverage Docker layer cache
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw

# Download all dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -q

# Copy source and build the fat JAR
COPY src/ src/
RUN mvn clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────
# Stage 2 — RUNTIME: slim JRE 21 image, no build tools
# ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy only the built JAR from the builder stage
COPY --from=builder /build/target/tasktracker-backend-1.0.0.jar app.jar

# Render injects $PORT at runtime; Spring Boot reads -Dserver.port
# Default 8080 is the Render Docker default
EXPOSE 8080

ENTRYPOINT ["java", \
  "-Dspring.profiles.active=prod", \
  "-Dserver.port=${PORT:-8080}", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
