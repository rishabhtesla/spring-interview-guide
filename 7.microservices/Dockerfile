FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy
ARG SERVICE
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && apt-get clean
WORKDIR /app
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-1.0.0.jar /app/app.jar
USER 10001:10001
HEALTHCHECK --interval=5s --timeout=3s --start-period=30s --retries=20 \
    CMD curl --fail --silent http://localhost:${SERVER_PORT}/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
