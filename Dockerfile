FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace
COPY backend/pom.xml backend/mvnw backend/mvnw.cmd ./
COPY backend/.mvn ./.mvn
RUN chmod +x mvnw
RUN ./mvnw -B dependency:go-offline

COPY backend/src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/target/bolsopass-api-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
