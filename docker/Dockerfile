FROM eclipse-temurin:21-jdk@sha256:57865c22b954cf920cb05a610af81d577e89783282514ba071e99c7357f6c769 AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN ./gradlew :${MODULE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy@sha256:3097cbbebb7d490494a98aed2301f284b38f79eba158eef098c6fc8c8af11c23
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
ARG MODULE
COPY --from=build /workspace/${MODULE}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
