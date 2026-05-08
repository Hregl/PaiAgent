FROM maven:3.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/data/audio
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.datasource.url=jdbc:sqlite:/app/data/paiagent.db"]
