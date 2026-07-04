FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY build/libs/front-ui.jar app.jar

EXPOSE 8085

HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD curl -fsS http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
