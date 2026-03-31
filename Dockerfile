# ==============================================================================
# Stage 1: Build the Spring Boot fat JAR
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# Copy Maven wrapper and POM first (better layer caching — deps change less often)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src/ src/
RUN ./mvnw package -DskipTests -B && \
    mv target/*.jar target/app.jar

# ==============================================================================
# Stage 2: Runtime image
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Install runtime dependencies:
#   tini       — proper PID 1 / signal handling
#   nodejs npm — required for Playwright MCP (npx)
#   git        — used by OpenCode agents for version control
#   curl       — health checks, OpenCode install
#   bash       — scripts / OpenCode shell operations
RUN apk add --no-cache tini nodejs npm git curl bash

# Install OpenCode CLI (installer puts binary in /root/.opencode/bin/)
RUN curl -fsSL https://opencode.ai/install | bash && \
    cp /root/.opencode/bin/opencode /usr/local/bin/opencode && \
    chmod +x /usr/local/bin/opencode

# Create non-root user for running the app
RUN addgroup -S base66 && adduser -S base66 -G base66

# Create workspace and OpenCode data directories
RUN mkdir -p /data/workspaces /home/base66/.config/opencode /home/base66/.local/share/opencode && \
    chown -R base66:base66 /data /home/base66

# Copy the fat JAR from the build stage
COPY --from=build --chown=base66:base66 /build/target/app.jar /app/app.jar

# Switch to non-root user
USER base66
WORKDIR /app

# Expose only the Spring Boot port (OpenCode on 4096 stays internal)
EXPOSE 8080

# Use tini as PID 1
ENTRYPOINT ["tini", "--"]

# Start the Spring Boot application (which auto-starts OpenCode via ProcessBuilder)
CMD ["java", "-jar", "app.jar"]
