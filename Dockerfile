# Stage 1: build the executable jar with a full JDK
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only what's needed to resolve dependencies first, so this layer is
# cached and skipped on rebuilds where only application source changed.
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
# Tests require Testcontainers (a Docker daemon), which isn't available
# during image build; run `./mvnw test` separately before building the image.
RUN ./mvnw package -DskipTests -B

# Stage 2: run the jar with a minimal JRE (no compiler/build tools needed at runtime)
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]