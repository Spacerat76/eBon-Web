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
