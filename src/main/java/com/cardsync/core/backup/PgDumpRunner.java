package com.cardsync.core.backup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Roda {@code pg_dump} contra o próprio banco cardsync desta instância (formato custom,
 * restaurável via {@code pg_restore}). A URL/usuário/senha já configurados em
 * spring.datasource.* são suficientes — não precisa de nenhuma credencial nova. Usado por
 * BackupService.
 */
@Slf4j
@Component
public class PgDumpRunner {

  private static final long TIMEOUT_MINUTES = 10;

  @Value("${spring.datasource.url}")
  private String datasourceUrl;

  @Value("${spring.datasource.username}")
  private String username;

  @Value("${spring.datasource.password}")
  private String password;

  public byte[] dump() {
    JdbcConnectionInfo info = parseJdbcUrl(datasourceUrl);
    Path tempFile = null;
    try {
      tempFile = Files.createTempFile("cardsync-backup-", ".dump");

      ProcessBuilder processBuilder = new ProcessBuilder(
        resolvePgDumpExecutable(),
        "--host=" + info.host(),
        "--port=" + info.port(),
        "--username=" + username,
        "--dbname=" + info.database(),
        "--format=custom",
        "--file=" + tempFile
      );
      processBuilder.environment().put("PGPASSWORD", password);
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();
      String output;
      try (InputStream processOutput = process.getInputStream()) {
        output = new String(processOutput.readAllBytes(), StandardCharsets.UTF_8);
      }

      boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("pg_dump excedeu o tempo limite de " + TIMEOUT_MINUTES + " minutos");
      }
      if (process.exitValue() != 0) {
        throw new IllegalStateException("pg_dump falhou (exit=" + process.exitValue() + "): " + output);
      }

      return Files.readAllBytes(tempFile);
    } catch (IOException e) {
      throw new IllegalStateException("Falha ao executar pg_dump", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Backup interrompido durante a execução do pg_dump", e);
    } finally {
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException e) {
          log.warn("Não foi possível remover o arquivo temporário de backup {}: {}", tempFile, e.getMessage());
        }
      }
    }
  }

  /**
   * Em produção (imagem Docker, ver Dockerfile) o postgresql-client instalado via apt já deixa
   * pg_dump no PATH. Em máquina de desenvolvimento Windows isso raramente é verdade (o
   * instalador do PostgreSQL não adiciona ao PATH por padrão) — mesmo problema já resolvido em
   * scripts/backup-db.ps1, aqui replicado em Java: tenta o PATH primeiro, senão procura nas
   * instalações padrão do PostgreSQL em Program Files.
   */
  private String resolvePgDumpExecutable() {
    boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
    String executableName = windows ? "pg_dump.exe" : "pg_dump";

    String path = System.getenv("PATH");
    if (path != null) {
      for (String dir : path.split(File.pathSeparator)) {
        File candidate = new File(dir, executableName);
        if (candidate.isFile()) {
          return candidate.getAbsolutePath();
        }
      }
    }

    if (windows) {
      for (String root : new String[]{"C:/Program Files/PostgreSQL", "C:/Program Files (x86)/PostgreSQL"}) {
        File rootDir = new File(root);
        File[] versionDirs = rootDir.listFiles(File::isDirectory);
        if (versionDirs == null) continue;

        File found = java.util.Arrays.stream(versionDirs)
          .sorted(Comparator.comparing(File::getName).reversed())
          .map(dir -> new File(dir, "bin/" + executableName))
          .filter(File::isFile)
          .findFirst()
          .orElse(null);

        if (found != null) {
          return found.getAbsolutePath();
        }
      }
    }

    // Não encontrado em nenhum lugar conhecido — deixa o nome puro, o ProcessBuilder vai falhar
    // com uma mensagem clara de "arquivo não encontrado" (mesmo comportamento de antes).
    return executableName;
  }

  private record JdbcConnectionInfo(String host, int port, String database) {}

  /** Espera sempre o formato {@code jdbc:postgresql://host:port/database[?params]}. */
  private JdbcConnectionInfo parseJdbcUrl(String url) {
    String withoutPrefix = url.replaceFirst("^jdbc:postgresql://", "");
    int slashIndex = withoutPrefix.indexOf('/');
    String hostPort = withoutPrefix.substring(0, slashIndex);
    String afterSlash = withoutPrefix.substring(slashIndex + 1);
    int queryIndex = afterSlash.indexOf('?');
    String database = queryIndex >= 0 ? afterSlash.substring(0, queryIndex) : afterSlash;

    String[] hostPortParts = hostPort.split(":", 2);
    String host = hostPortParts[0];
    int port = hostPortParts.length > 1 ? Integer.parseInt(hostPortParts[1]) : 5432;

    return new JdbcConnectionInfo(host, port, database);
  }
}
