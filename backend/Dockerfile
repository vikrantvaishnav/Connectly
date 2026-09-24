# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S connectly && adduser -S connectly -G connectly
USER connectly

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
