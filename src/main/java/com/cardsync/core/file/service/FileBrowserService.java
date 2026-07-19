package com.cardsync.core.file.service;

import com.cardsync.bff.controller.v1.representation.model.fileprocessing.FileBrowserItemModel;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Lê (lista/baixa) os arquivos já gravados nas pastas de processamento — a contraparte de
 * leitura do {@link FileUploadService}. Não move nem apaga nada; é só uma visão somente-leitura
 * sobre o mesmo Volume que o scheduler/processadores usam, para quem precisa inspecionar um
 * arquivo específico sem acessar o Console do provedor de deploy diretamente.
 */
@Service
@RequiredArgsConstructor
public class FileBrowserService {

  private final FileProcessingProperties properties;

  public List<FileBrowserItemModel> list(String system, String folder) {
    Path dir = resolveFolder(system, folder);

    if (!Files.isDirectory(dir)) {
      return List.of();
    }

    // Percorre recursivamente: processed/error/duplicate organizam os arquivos em subpastas
    // (ex.: <ano>/ para ERP e adquirente, <ano>/agencia-x_conta-y/ para banco) para não deixar
    // uma única pasta crescer sem limite — Files.list (não recursivo) nunca enxergaria os
    // arquivos de verdade, só as subpastas de ano.
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream
        .filter(Files::isRegularFile)
        .map(file -> toItem(dir, file))
        .sorted(Comparator.comparing(FileBrowserItemModel::lastModified).reversed())
        .toList();
    } catch (IOException ex) {
      throw new UncheckedIOException("Falha ao listar arquivos em " + dir, ex);
    }
  }

  public Resource loadForDownload(String system, String folder, String relativePath) {
    Path dir = resolveFolder(system, folder);
    Path target = sanitizeRelativePath(dir, relativePath);

    if (target == null || !Files.isRegularFile(target)) {
      throw BusinessException.notFound(ErrorCode.NOT_FOUND, "Arquivo não encontrado: " + relativePath);
    }

    return new FileSystemResource(target);
  }

  /** {@code name} carrega o caminho relativo à pasta (ex.: "2024/arquivo.csv"), não só o nome puro —
   * necessário porque processed/error/duplicate organizam os arquivos em subpastas por ano/conta. */
  private FileBrowserItemModel toItem(Path baseDir, Path file) {
    try {
      BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
      Instant modifiedAt = attrs.lastModifiedTime().toInstant();
      String relativeName = baseDir.relativize(file).toString().replace('\\', '/');
      return new FileBrowserItemModel(
        relativeName,
        attrs.size(),
        OffsetDateTime.ofInstant(modifiedAt, ZoneOffset.UTC)
      );
    } catch (IOException ex) {
      throw new UncheckedIOException("Falha ao ler atributos de " + file, ex);
    }
  }

  private Path resolveFolder(String system, String folder) {
    FileProcessingProperties.FilePaths paths = properties.resolveSystemPaths(system);
    if (paths == null) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "Sistema de arquivo inválido ou não configurado: " + system);
    }

    if (folder == null || folder.isBlank()) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "Informe a pasta a ser listada.");
    }

    String dirPath = switch (folder.trim().toLowerCase(Locale.ROOT)) {
      case "input" -> paths.getInput();
      case "processed" -> paths.getProcessed();
      case "error" -> paths.getError();
      case "duplicate" -> paths.getDuplicate();
      case "invalid_file", "invalid" -> paths.getInvalid();
      case "log" -> paths.getLog();
      default -> null;
    };

    if (dirPath == null || dirPath.isBlank()) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "Pasta inválida ou não configurada: " + folder);
    }

    return Paths.get(dirPath);
  }

  /**
   * Resolve {@code relativePath} (ex.: "2024/arquivo.csv" ou "2023/agencia-x_conta-y/arquivo.ret")
   * contra {@code baseDir}, rejeitando qualquer caminho absoluto ou que — depois de normalizado —
   * escape da pasta base (proteção contra path traversal via "..").
   */
  private Path sanitizeRelativePath(Path baseDir, String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      return null;
    }

    Path candidate = Paths.get(relativePath);
    if (candidate.isAbsolute()) {
      return null;
    }

    Path resolved = baseDir.resolve(candidate).normalize();
    if (!resolved.startsWith(baseDir)) {
      return null;
    }

    return resolved;
  }
}
