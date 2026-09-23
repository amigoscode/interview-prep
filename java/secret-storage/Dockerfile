FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
# Dependencies get their own layer, so a source change does not download them again.
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
# Tests run here: a failing test fails the image build.
RUN mvn -q package

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app/target/secret-storage-interview-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
# No secret is baked into the image: ADMIN_PASSWORD_HASH is supplied when the container starts.
ENTRYPOINT ["java", "-jar", "app.jar"]
