## Multi-stage Dockerfile for building and running the backend

# Build stage
FROM maven:3.9.4-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/ebon-backend-0.1.0-SNAPSHOT.jar ./app.jar
EXPOSE 8080
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/ebon
ENV SPRING_DATASOURCE_USERNAME=postgres
ENV SPRING_DATASOURCE_PASSWORD=postgres
ENTRYPOINT ["java","-jar","/app/app.jar"]
# Multi-stage build for backend
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/ebon-backend-*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
