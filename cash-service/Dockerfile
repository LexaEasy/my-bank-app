FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY build/libs/cash-service.jar app.jar

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=5s --retries=5 CMD curl -fsS http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
