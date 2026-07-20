FROM gradle:8.10-jdk17 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew ./
COPY src src
RUN gradle bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Compose healthcheck가 실제 actuator 응답까지 검증할 수 있도록 최소 HTTP client를 포함한다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -m farmlog
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN mkdir -p /app/uploads /app/exports && chown -R farmlog:farmlog /app
USER farmlog
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
