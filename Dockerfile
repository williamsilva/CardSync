# Nota (2026-09-07): variáveis de serviço do Railway só chegam ao container em runtime, nunca ao
# processo de build do Dockerfile, a menos que sejam declaradas com ARG no stage que precisa delas
# (Railway então injeta como --build-arg automaticamente) - mesmo achado feito no NimbusFlowServer/
# NimbusNovaxServer (Gradle) ao consumir o NimbusCommonsServer pela primeira vez. Aqui (Maven) o
# equivalente ao gradle.properties é o ~/.m2/settings.xml - gerado abaixo a partir dos ARGs, nunca
# hardcoded na imagem final (só existe no stage de build, descartado no stage runtime).
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
RUN mkdir -p /root/.m2 && \
    echo "<settings><servers><server><id>github</id><username>${GITHUB_ACTOR}</username><password>${GITHUB_TOKEN}</password></server></servers></settings>" > /root/.m2/settings.xml

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
# server.port real é 9091 (ver application.yml/application-dev.yml, PORT env var) - 8080 aqui era
# só o default do Spring Boot, nunca batia com o que a aplicação de fato escuta.
EXPOSE 9091
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]