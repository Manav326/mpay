# syntax=docker/dockerfile:1.7

FROM gradle:9.6.0-jdk21-ubi10 AS build

WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src

RUN chmod +x gradlew
RUN gradle clean test bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine-3.24 AS runtime

WORKDIR /app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25"

RUN addgroup --system --gid 10001 mpay \
    && adduser --system --uid 10001 --ingroup mpay mpay \
    && mkdir -p /app/config /app/data/profile-images /app/data/rental-images \
    && chown mpay:mpay /app/config /app/data/profile-images /app/data/rental-images

COPY --from=build --chown=mpay:mpay /app/build/libs/*.jar app.jar

USER mpay

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
