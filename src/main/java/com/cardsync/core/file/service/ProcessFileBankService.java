package com.cardsync.core.file.service;

import com.cardsync.core.file.bank.Cnab240BankLayout;
import com.cardsync.core.file.bank.Cnab240FileProcessor;
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
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFileBankService {
  private final MoveFileService moveFileService;
  private final Cnab240FileProcessor cnab240FileProcessor;
  private final FileProcessingProperties fileProcessingProperties;

  public void processFiles() {
    Map<String, FileProcessingProperties.FilePaths> bankPaths = fileProcessingProperties.getBankPaths();

    if (bankPaths.isEmpty()) {
      throw new IllegalStateException("Nenhum banco configurado em file-processing.systems.bank.<banco>.input");
    }

    int processed = 0;
    int invalid = 0;
    int errors = 0;
    int scanned = 0;

    for (var entry : bankPaths.entrySet()) {
      String bankKey = entry.getKey();
      FileProcessingProperties.FilePaths paths = entry.getValue();
      BankProcessingResult result = processBankFolder(bankKey, paths);
      scanned += result.scanned();
      processed += result.processed();
      invalid += result.invalid();
      errors += result.errors();
    }

    log.info("✅ Processamento bancário finalizado: bancosConfigurados={}, arquivosEncontrados={}, processados={}, invalidos={}, erros={}",
      bankPaths.size(), scanned, processed, invalid, errors);

    if (errors > 0 && processed == 0) {
      throw new IllegalStateException("Processamento bancário finalizado com " + errors + " erro(s) e " + processed + " arquivo(s) processado(s).");
    }
  }

  private BankProcessingResult processBankFolder(String bankKey, FileProcessingProperties.FilePaths paths) {
    int processed = 0;
    int invalid = 0;
    int errors = 0;
    int scanned = 0;

    Path inputPath = Paths.get(paths.getInput());
    if (!Files.exists(inputPath)) {
      log.info("ℹ Pasta bancária {} ainda não existe: {}", bankKey, inputPath);
      return new BankProcessingResult(0, 0, 0, 0);
    }

    try (DirectoryStream<Path> files = Files.newDirectoryStream(inputPath)) {
      boolean hasFiles = false;
      for (Path file : files) {
        if (!Files.isRegularFile(file)) continue;
        hasFiles = true;
        scanned++;

        try {
          if (!FileUtil.isTextFile(file)) {
            log.info("ℹ Arquivo bancário {} inválido para {}: não é texto. Movendo para invalid_file.", file.getFileName(), bankKey);
            moveFileService.moveNow(file, paths.getInvalid());
            invalid++;
            continue;
          }

          FileResult result = validateAndProcess(bankKey, file, paths);
          if (result == FileResult.PROCESSED) processed++;
          if (result == FileResult.INVALID) invalid++;
          if (result == FileResult.ERROR) errors++;
        } catch (Exception ex) {
          errors++;
          log.error("❌ Erro ao processar arquivo bancário {} em {}: {}", file.getFileName(), bankKey, ex.getMessage(), ex);
          if (Files.exists(file)) {
            moveFileService.moveNow(file, paths.getError());
          }
        }
      }

      if (!hasFiles) {
        log.debug("Nenhum arquivo bancário encontrado para {} em {}", bankKey, paths.getInput());
      }
    } catch (Exception ex) {
      errors++;
      log.error("❌ Erro ao acessar/processar pasta bancária {}: {}", bankKey, ex.getMessage(), ex);
    }

    return new BankProcessingResult(scanned, processed, invalid, errors);
  }

  private FileResult validateAndProcess(String bankKey, Path file, FileProcessingProperties.FilePaths paths) {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String firstLine = reader.readLine();
      String bankCode = FileParserUtils.extractStringLine(firstLine, "0-3", 1);
      String recordType = FileParserUtils.extractStringLine(firstLine, "7-8", 1);
      Cnab240BankLayout layout = Cnab240BankLayout.fromBankCode(bankCode);

      if (layout != null && "0".equals(recordType)) {
        cnab240FileProcessor.processFile(file, paths, layout);
        return FileResult.PROCESSED;
      }

      String preview = firstLine == null ? "" : firstLine.substring(0, Math.min(firstLine.length(), 40));
      log.info("ℹ Arquivo bancário não reconhecido em {}: {}. Movendo para invalid_file. bankCode={}, recordType={}, primeiros40='{}', tamanhoLinha={}",
        bankKey, file.getFileName(), bankCode, recordType, preview, firstLine == null ? 0 : firstLine.length());

      moveFileService.moveNow(file, paths.getInvalid());
      return FileResult.INVALID;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar arquivo bancário {} em {}: {}", file.getFileName(), bankKey, ex.getMessage(), ex);

      if (Files.exists(file)) {
        moveFileService.moveNow(file, paths.getError());
      } else {
        log.warn("⚠ Arquivo bancário {} já foi movido por outro fluxo; ignorando novo movimento para erro.", file.getFileName());
      }
      return FileResult.ERROR;
    }
  }

  private enum FileResult {
    PROCESSED,
    INVALID,
    ERROR
  }

  private record BankProcessingResult(int scanned, int processed, int invalid, int errors) {
  }
}
