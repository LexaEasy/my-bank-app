FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/notifications-service.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
