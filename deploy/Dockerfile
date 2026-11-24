FROM eclipse-temurin:25-jre
WORKDIR /app
COPY server/target/reservation-server-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]