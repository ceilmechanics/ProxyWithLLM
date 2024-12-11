FROM maven:3.8-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
COPY src/main/resources/ca.crt /app/ca.crt
RUN mvn clean package assembly:single -DskipTests

FROM openjdk:17-slim
WORKDIR /app
COPY --from=build /app/target/*-with-dependencies.jar app.jar
COPY --from=build /app/ca.crt /app/ca.crt
RUN useradd -m javauser
USER javauser
ENTRYPOINT ["java", "-jar", "app.jar"]