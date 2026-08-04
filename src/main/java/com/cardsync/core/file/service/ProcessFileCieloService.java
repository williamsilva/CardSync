package com.cardsync.core.file.service;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.FileHashService;
import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.core.file.util.FileUtil;
import com.cardsync.core.file.util.MoveFileService;
import com.cardsync.domain.repository.ProcessedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * Ponto de entrada para arquivos "Extrato Eletrônico" da Cielo. Lê a primeira linha (registro
 * "0" — Header) e roteia pelo campo "Tipo de arquivo" (posição 48-49 do manual) — ver Tabela I:
 * "03" Captura/Previsão, "04" Liquidação/Pagamento, "09" Saldo em aberto, "15" Negociação de
 * Recebíveis, "16" Pix. "03" e "04" estão implementados (ver ProcessCielo03Service/
 * ProcessCielo04Service); os demais são movidos para invalid_file com um log claro (não é erro,
 * é escopo futuro).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFileCieloService {

  private static final Charset CIELO_CHARSET = Charset.forName("windows-1252");

  private final MoveFileService moveFileService;
  private final FileHashService fileHashService;
  private final ProcessCielo03Service processCielo03Service;
  private final ProcessCielo04Service processCielo04Service;
  private final ProcessedFileRepository processedFileRepository;
  private final FileProcessingProperties fileProcessingProperties;

  public void processFiles() {
    var paths = fileProcessingProperties.getPathsOrThrow("cielo");
    paths.ensureDirectories();
    int processed = 0;
    int errors = 0;

    Path inputPath = Paths.get(paths.getInput()).toAbsolutePath().normalize();
    if (!Files.exists(inputPath)) {
      log.warn("⚠ Nenhum arquivo Cielo encontrado em {}", paths.getInput());
      return;
    }

    try (Stream<Path> walk = Files.walk(inputPath)) {
      List<Path> files = walk.filter(Files::isRegularFile).toList();

      if (files.isEmpty()) {
        log.warn("⚠ Nenhum arquivo Cielo encontrado em {}", paths.getInput());
      }

      for (Path file : files) {
        try {
          if (!FileUtil.isTextFile(file)) {
            moveFileService.moveNow(file, paths.getInvalid());
            processed++;
            continue;
          }

          String contentHash = fileHashService.sha256(file);
          var originalProcessedFile = processedFileRepository.findFirstByContentHash(contentHash);
          if (originalProcessedFile.isPresent()) {
            log.warn(
              "⚠ Arquivo Cielo duplicado por conteúdo: nome={}, sha256={}. Movendo para {}.",
              file.getFileName(),
              contentHash,
              paths.getDuplicate()
            );
            moveFileService.moveNow(file, paths.getDuplicate(), originalProcessedFile.get().getDateFile());
            processed++;
            continue;
          }

          if (validateAndProcess(file, paths, contentHash)) {
            processed++;
          } else {
            errors++;
          }
        } catch (Exception ex) {
          errors++;
          log.error("❌ Erro ao processar arquivo Cielo {}: {}", file.getFileName(), ex.getMessage(), ex);
          if (Files.exists(file)) {
            moveFileService.moveNow(file, paths.getError());
          }
        }
      }

      if (errors > 0) {
        log.warn("⚠ Processamento Cielo finalizado com {} erro(s) e {} arquivo(s) processado(s). "
          + "Os arquivos com erro foram movidos para a pasta de erro; a esteira continua.", errors, processed);
      } else {
        log.info("✅ Processamento Cielo finalizado. {} arquivo(s) processado(s).", processed);
      }
    } catch (Exception ex) {
      log.error("❌ Erro ao acessar/processar pasta Cielo: {}", ex.getMessage(), ex);
      throw new IllegalStateException(ex);
    }
  }

  private boolean validateAndProcess(Path file, FileProcessingProperties.FilePaths paths, String contentHash) {
    try (BufferedReader reader = Files.newBufferedReader(file, CIELO_CHARSET)) {
      String firstLine = reader.readLine();
      String recordType = FileParserUtils.extractStringLine(firstLine, "0-1", 1);
      String fileType = FileParserUtils.extractStringLine(firstLine, "47-49", 1);

      if ("0".equals(recordType) && "03".equals(fileType)) {
        processCielo03Service.processFile(file, paths, contentHash);
        return true;
      }

      if ("0".equals(recordType) && "04".equals(fileType)) {
        processCielo04Service.processFile(file, paths, contentHash);
        return true;
      }

      String preview = firstLine == null ? "" : firstLine.substring(0, Math.min(firstLine.length(), 40));
      log.info(
        "ℹ Arquivo Cielo tipo '{}' ainda não suportado ({}). Movendo para invalid_file. primeiros40='{}', tamanhoLinha={}",
        fileType, file.getFileName(), preview, firstLine == null ? 0 : firstLine.length()
      );
      moveFileService.moveNow(file, paths.getInvalid());
      return true;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar arquivo Cielo {}: {}", file.getFileName(), ex.getMessage(), ex);

      if (Files.exists(file)) {
        moveFileService.moveNow(file, paths.getError());
      } else {
        log.warn("⚠ Arquivo Cielo {} já foi movido por outro fluxo; ignorando novo movimento para erro.", file.getFileName());
      }

      return false;
    }
  }
}
