FROM maven:3.9-eclipse-temurin-21-alpine as build
WORKDIR /workspace/app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp
COPY --from=build /workspace/app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
