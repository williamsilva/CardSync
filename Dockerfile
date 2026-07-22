FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -DskipTests dependency:go-offline --no-transfer-progress

COPY src ./src
RUN mvn -DskipTests clean package --no-transfer-progress

FROM eclipse-temurin:21-jre
WORKDIR /app
# postgresql-client fornece o pg_dump usado pelo backup sob demanda do próprio banco
# (ver PgDumpRunner/BackupService).
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client \
  && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]