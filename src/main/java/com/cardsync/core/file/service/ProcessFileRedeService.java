package com.cardsync.core.file.service;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.core.file.util.FileUtil;
import com.cardsync.core.file.util.MoveFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFileRedeService {
  private final MoveFileService moveFileService;
  private final ProcessRedeEeVcService processRedeEeVcService;
  private final ProcessRedeEeFiService processRedeEeFiService;
  private final ProcessRedeEeVdService processRedeEeVdService;
  private final FileProcessingProperties fileProcessingProperties;

  public void processFiles() {
    var paths = fileProcessingProperties.getPathsOrThrow("rede");
    int processed = 0;
    int errors = 0;

    try (DirectoryStream<Path> files = Files.newDirectoryStream(Paths.get(paths.getInput()))) {
      boolean hasFiles = false;
      for (Path file : files) {
        if (!Files.isRegularFile(file)) continue;
        hasFiles = true;

        try {
          if (!FileUtil.isTextFile(file)) {
            moveFileService.moveNow(file, paths.getInvalid());
            processed++;
            continue;
          }

          if (validateAndProcess(file, paths)) {
            processed++;
          } else {
            errors++;
          }
        } catch (Exception ex) {
          errors++;
          log.error("❌ Erro ao processar arquivo Rede {}: {}", file.getFileName(), ex.getMessage(), ex);
          if (Files.exists(file)) {
            moveFileService.moveNow(file, paths.getError());
          }
        }
      }

      if (!hasFiles) {
        log.warn("⚠ Nenhum arquivo Rede encontrado em {}", paths.getInput());
      }

      if (errors > 0) {
        throw new IllegalStateException("Processamento Rede finalizado com " + errors + " erro(s) e " + processed + " arquivo(s) processado(s).");
      }
    } catch (Exception ex) {
      log.error("❌ Erro ao acessar/processar pasta Rede: {}", ex.getMessage(), ex);
      throw new IllegalStateException(ex);
    }
  }

  private boolean validateAndProcess(Path file, FileProcessingProperties.FilePaths paths) {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String firstLine = reader.readLine();
      String identifier = resolveIdentifier(firstLine);

      if ("002".equals(identifier)) {
        processRedeEeVcService.processFile(file, paths);
        return true;
      }

      if ("030".equals(identifier)) {
        processRedeEeFiService.processFile(file, paths);
        return true;
      }

      if (isEevdHeader(firstLine, identifier)) {
        processRedeEeVdService.processFile(file, paths);
        return true;
      }

      String preview = firstLine == null ? "" : firstLine.substring(0, Math.min(firstLine.length(), 40));
      log.info("ℹ Header {} não reconhecido para arquivo {}. Movendo para invalid_file. primeiros40='{}', tamanhoLinha={}",
        identifier, file.getFileName(), preview, firstLine == null ? 0 : firstLine.length());

      moveFileService.moveNow(file, paths.getInvalid());
      return true;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar arquivo Rede {}: {}", file.getFileName(), ex.getMessage(), ex);

      if (Files.exists(file)) {
        moveFileService.moveNow(file, paths.getError());
      } else {
        log.warn("⚠ Arquivo Rede {} já foi movido por outro fluxo; ignorando novo movimento para erro.", file.getFileName());
      }

      return false;
    }
  }

  private String resolveIdentifier(String firstLine) {
    if (firstLine == null || firstLine.isBlank()) return null;

    int comma = firstLine.indexOf(',');
    if (comma > 0) {
      return firstLine.substring(0, comma).trim();
    }

    return FileParserUtils.extractStringLine(firstLine, "0-3", 1);
  }

  private boolean isEevdHeader(String firstLine, String identifier) {
    if (!"00".equals(identifier) || firstLine == null) return false;

    String normalized = Normalizer.normalize(firstLine, Normalizer.Form.NFD)
      .replaceAll("\\p{M}", "")
      .toUpperCase(Locale.ROOT);

    return normalized.contains("EEVD")
      || normalized.contains("VENDAS DEBITO")
      || normalized.contains("CARTOES DE DEBITO")
      || normalized.contains("MOVIMENTACAO DIARIA");
  }
}
