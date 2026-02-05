# demo/Dockerfile
# Build and run a single module using Java 25
# Usage:
#   docker build -f Dockerfile --build-arg APP_MODULE=scheduler-api -t scheduler-api:demo .
#   docker build -f Dockerfile --build-arg APP_MODULE=scheduler-master -t scheduler-master:demo .
#   docker build -f Dockerfile --build-arg APP_MODULE=scheduler-worker -t scheduler-worker:demo .



#docker image rm scheduler-api:demo
#docker image rm scheduler-master:demo 

ARG JAVA_VERSION=25

############################
# Stage 1: Build (Java 25)
############################
FROM eclipse-temurin:${JAVA_VERSION}-jdk AS build

ARG APP_MODULE
WORKDIR /workspace

# Copy build files first for layer caching
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY gradle/libs.versions.toml ./gradle/libs.versions.toml

# Copy all modules (needed for Gradle multi-module build)
COPY scheduler-common ./scheduler-common
COPY scheduler-spi ./scheduler-spi
COPY scheduler-meter ./scheduler-meter
COPY scheduler-domain ./scheduler-domain
COPY scheduler-service ./scheduler-service
COPY scheduler-dao ./scheduler-dao
COPY scheduler-adapter-jdbc ./scheduler-adapter-jdbc
COPY scheduler-adapter-kafka ./scheduler-adapter-kafka
COPY scheduler-adapter-inmemory ./scheduler-adapter-inmemory
COPY scheduler-remote ./scheduler-remote
COPY scheduler-api ./scheduler-api
COPY scheduler-master ./scheduler-master
COPY scheduler-worker ./scheduler-worker
COPY scheduler-alert-server ./scheduler-alert-server

# Make wrapper executable (Linux containers)
RUN chmod +x ./gradlew

# Build just the requested module
RUN ./gradlew --no-daemon :${APP_MODULE}:clean :${APP_MODULE}:bootJar

############################
# Stage 2: Runtime (Java 25)
############################
FROM eclipse-temurin:${JAVA_VERSION}-jre

ARG APP_MODULE
WORKDIR /app

# Copy the built jar
COPY --from=build /workspace/${APP_MODULE}/build/libs/*.jar /app/app.jar

# Default JVM opts (override via JAVA_TOOL_OPTIONS in compose)
ENV JAVA_OPTS=""

EXPOSE 8080
ENTRYPOINT ["sh", "-lc", "java $JAVA_OPTS -jar /app/app.jar"]
