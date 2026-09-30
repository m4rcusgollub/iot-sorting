# Build stage (Java 21 conforme definido no pom.xml)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY src src
RUN mvn clean package -DskipTests

# Runtime stage (Java 21 JRE)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/iot-sorting-*.jar app.jar

ENV PORT=8080
EXPOSE 8080

# Fuso horario do backend publicado: dataHora, ultimaDeteccao e objetosHoje passam a
# ser gravados em horario local (equivalente a variavel de ambiente TZ=America/Sao_Paulo).
ENTRYPOINT ["java", "-Duser.timezone=America/Sao_Paulo", "-jar", "app.jar"]

