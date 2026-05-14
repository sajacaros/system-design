FROM eclipse-temurin:17-jre

WORKDIR /app
ARG JAR_FILE=build/libs/kv-store-demo-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
