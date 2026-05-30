FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S eagle-bank && adduser -S eagle-bank -G eagle-bank
COPY --from=build /app/target/*.jar app.jar
RUN chown eagle-bank:eagle-bank app.jar
USER eagle-bank
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
