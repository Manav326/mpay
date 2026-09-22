# syntax=docker/dockerfile:1.7

FROM gradle:9.6.0-jdk21-ubi10 AS build

WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src

RUN chmod +x gradlew
RUN gradle clean test bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25"

COPY --from=build /app/build/libs/*.jar app.jar

RUN mkdir -p /app/config /app/data/profile-images /app/data/rental-images \
    && addgroup --system --gid 10001 mpay \
    && adduser --system --uid 10001 --ingroup mpay mpay \
    && chown -R mpay:mpay /app

USER mpay

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
