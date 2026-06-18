FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8084

HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD curl -fsS http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
