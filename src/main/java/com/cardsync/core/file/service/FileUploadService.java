package com.cardsync.core.file.service;

import com.cardsync.bff.controller.v1.representation.model.fileprocessing.FileUploadItemResultModel;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recebe uploads manuais de arquivos (EEFI/CNAB de adquirentes e bancos, arquivos ERP) e os
 * grava diretamente na pasta {@code input} do sistema de destino — o mesmo lugar onde, hoje,
 * uma pessoa copiaria o arquivo manualmente. Depois disso, o arquivo segue o fluxo normal
 * (scheduler ou disparo manual em {@link FileStorageTask}), sem nenhuma diferença de
 * processamento entre um arquivo copiado à mão e um arquivo recebido por este endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

  private final FileProcessingProperties properties;

  public List<FileUploadItemResultModel> upload(String system, MultipartFile[] files) {
    if (files == null || files.length == 0) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "Nenhum arquivo enviado.");
    }

    FileProcessingProperties.FilePaths paths = resolvePaths(system);
    paths.ensureDirectories();

    List<FileUploadItemResultModel> results = new ArrayList<>();
    for (MultipartFile file : files) {
      results.add(saveOne(paths.getInput(), file));
    }

    log.info(
      "📥 Upload manual de arquivo(s): sistema={}, total={}, sucesso={}",
      system, results.size(), results.stream().filter(FileUploadItemResultModel::success).count()
    );

    return results;
  }

  private FileProcessingProperties.FilePaths resolvePaths(String system) {
    if (system == null || system.isBlank()) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "Informe o sistema de destino do arquivo.");
    }

    String key = system.trim().toLowerCase(Locale.ROOT);

    if ("erp".equals(key) || "rede".equals(key)) {
      return properties.getPathsOrThrow(key);
    }

    FileProcessingProperties.FilePaths paths = properties.getAcquirerPaths().get(key);
    if (paths == null) {
      paths = properties.getBankPaths().get(key);
    }
    if (paths == null) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "Sistema de arquivo inválido ou não configurado: " + system);
    }

    return paths;
  }

  private FileUploadItemResultModel saveOne(String inputDir, MultipartFile file) {
    String originalName = file != null ? file.getOriginalFilename() : null;

    if (file == null || file.isEmpty()) {
      return new FileUploadItemResultModel(originalName, false, "Arquivo vazio.");
    }

    String safeName = sanitizeFileName(originalName);
    if (safeName == null) {
      return new FileUploadItemResultModel(originalName, false, "Nome de arquivo inválido.");
    }

    Path target = Paths.get(inputDir).resolve(safeName);

    try (InputStream in = file.getInputStream()) {
      Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      return new FileUploadItemResultModel(safeName, true, null);
    } catch (IOException ex) {
      log.warn("⚠ Falha ao salvar arquivo enviado. arquivo={}, destino={}, erro={}", safeName, target, ex.getMessage());
      return new FileUploadItemResultModel(safeName, false, "Falha ao salvar o arquivo: " + ex.getMessage());
    }
  }

  /** Extrai só o nome do arquivo (sem componentes de diretório) e rejeita tentativas de path traversal. */
  private String sanitizeFileName(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return null;
    }

    String name = Paths.get(originalFilename).getFileName().toString();
    if (name.isBlank() || name.contains("..")) {
      return null;
    }

    return name;
  }
}
