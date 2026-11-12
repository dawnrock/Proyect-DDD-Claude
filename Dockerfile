# Image for Render's free plan (docs/deploy/render.md).
# The application code is not changed for the deployment: the profile, the port
# and the demo users' passwords come from environment variables.

# --- Build stage: compile the JAR (CI already runs the tests with `mvn verify`)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml lombok.config ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package \
    && cp target/ddd-lab-*.jar target/app.jar

# --- Run stage: JRE only, non-root user
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 10001 app
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
USER app

# `java -jar` does not load the demo profile (only `spring-boot:run` does).
ENV SPRING_PROFILES_ACTIVE=demo
# Render's free instance has 512 MB.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

# Render gives the port in PORT; 8080 when it is not set.
CMD ["sh", "-c", "exec java -jar app.jar --server.port=${PORT:-8080}"]
