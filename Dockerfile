# syntax=docker/dockerfile:1

# ---- Build ----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so source changes do not re-download everything.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Runtime --------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system --gid 10001 examprep \
    && useradd --system --uid 10001 --gid examprep --home /app --shell /usr/sbin/nologin examprep

WORKDIR /app
COPY --from=build /build/target/examprep-1.0.0.jar /app/app.jar

RUN mkdir -p /app/data/uploads \
    && chown -R examprep:examprep /app

USER examprep
EXPOSE 8080

ENV SERVER_PORT=8080 \
    APP_STORAGE_LOCATION=/app/data/uploads

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
