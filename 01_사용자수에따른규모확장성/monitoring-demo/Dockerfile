# --- build stage ---
FROM gradle:8.10-jdk17 AS build
WORKDIR /app

COPY settings.gradle build.gradle ./
COPY src ./src

RUN gradle bootJar --no-daemon

# --- runtime stage ---
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# Heap intentionally small so memory load endpoint is easy to observe.
ENV JAVA_OPTS="-Xms128m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
