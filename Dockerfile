# Nota (2026-09-07): variáveis de serviço do Railway só chegam ao container em runtime, nunca ao
# processo de build do Dockerfile, a menos que sejam declaradas com ARG no stage que precisa delas
# (Railway então injeta como --build-arg automaticamente) - mesmo achado feito no NimbusFlowServer/
# NimbusNovaxServer (Gradle) ao consumir o NimbusCommonsServer pela primeira vez. Aqui (Maven) o
# equivalente ao gradle.properties é o ~/.m2/settings.xml.
#
# Fix 2026-09-19 (achado real: build local imprimiu o GITHUB_TOKEN em texto puro no log via
# stdout do `echo`, mesmo anti-padrão já corrigido no NimbusCoreServer em 2026-09-17/18): o
# settings.xml agora é só um TEMPLATE com `${env.*}` (sintaxe de interpolação de env var do
# próprio Maven, resolvida em memória na hora que o `mvn` roda) - o RUN que gera esse template não
# interpola o valor real em lugar nenhum, e o `export` que expõe o ARG como env var pro `mvn` não
# imprime nada (ao contrário do antigo `echo "...${TOKEN}..." > settings.xml`, que vazava o valor
# resolvido no log de build via stdout do echo). Mesmo padrão do NimbusCoreServer/Dockerfile.
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
WORKDIR /app
RUN mkdir -p /root/.m2 && \
    echo '<settings><servers><server><id>github</id><username>${env.GITHUB_ACTOR}</username><password>${env.GITHUB_TOKEN}</password></server><server><id>github-commons-server</id><username>${env.GITHUB_ACTOR}</username><password>${env.GITHUB_TOKEN}</password></server></servers></settings>' > /root/.m2/settings.xml

COPY pom.xml ./
RUN sh -c 'export GITHUB_ACTOR GITHUB_TOKEN; mvn -DskipTests dependency:go-offline --no-transfer-progress'

COPY src ./src
RUN sh -c 'export GITHUB_ACTOR GITHUB_TOKEN; mvn -DskipTests clean package --no-transfer-progress'

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