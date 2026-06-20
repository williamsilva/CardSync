package com.cardsync.core.file.service;

import com.cardsync.core.file.bank.Cnab240BankLayout;
import com.cardsync.core.file.bank.Cnab240FileProcessor;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.core.file.util.FileUtil;
import com.cardsync.core.file.util.MoveFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFileBankService {
  private static final Charset CNAB_CHARSET = Charset.forName("windows-1252");

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
    int duplicates = 0;
    int scanned = 0;

    for (var entry : bankPaths.entrySet()) {
      String bankKey = entry.getKey();
      FileProcessingProperties.FilePaths paths = entry.getValue();
      BankProcessingResult result = processBankFolder(bankKey, paths);
      scanned += result.scanned();
      processed += result.processed();
      invalid += result.invalid();
      errors += result.errors();
      duplicates += result.duplicates();
    }

    log.info("✅ Processamento bancário finalizado: bancosConfigurados={}, arquivosEncontrados={}, processados={}, duplicados={}, invalidos={}, erros={}",
      bankPaths.size(), scanned, processed, duplicates, invalid, errors);

    if (errors > 0) {
      log.warn("⚠ Processamento bancário teve {} erro(s) e {} arquivo(s) processado(s). "
        + "Os arquivos com erro foram movidos para a pasta de erro; a esteira continua.", errors, processed);
    }
  }

  private BankProcessingResult processBankFolder(String bankKey, FileProcessingProperties.FilePaths paths) {
    int processed = 0;
    int invalid = 0;
    int errors = 0;
    int duplicates = 0;
    int scanned = 0;

    paths.ensureDirectories();

    Path inputPath = Paths.get(paths.getInput()).toAbsolutePath().normalize();
    if (!Files.exists(inputPath)) {
      log.info("ℹ Pasta bancária {} ainda não existe: {}", bankKey, inputPath);
      return new BankProcessingResult(0, 0, 0, 0, 0);
    }

    Path invalidPath = paths.getInvalid() != null
      ? Paths.get(paths.getInvalid()).toAbsolutePath().normalize()
      : null;

    try (Stream<Path> walk = Files.walk(inputPath)) {
      List<Path> files = walk
        .filter(Files::isRegularFile)
        .filter(f -> invalidPath == null || !f.toAbsolutePath().normalize().startsWith(invalidPath))
        .toList();

      if (files.isEmpty()) {
        log.debug("Nenhum arquivo bancário encontrado para {} em {}", bankKey, paths.getInput());
      }

      for (Path file : files) {
        scanned++;
        try {
          if (!FileUtil.isTextFile(file)) {
            log.info("ℹ Arquivo bancário {} inválido para {}: não é texto. Movendo para invalid_file.", file.getFileName(), bankKey);
            moveFileService.moveNowBank(file, invalidDestination(paths), null, null, null);
            invalid++;
            continue;
          }

          FileResult result = validateAndProcess(bankKey, file, paths);
          if (result == FileResult.PROCESSED) processed++;
          if (result == FileResult.INVALID) invalid++;
          if (result == FileResult.DUPLICATE) duplicates++;
          if (result == FileResult.ERROR) errors++;
        } catch (Exception ex) {
          errors++;
          log.error("❌ Erro ao processar arquivo bancário {} em {}: {}", file.getFileName(), bankKey, ex.getMessage(), ex);
          if (Files.exists(file)) {
            moveFileService.moveNowBank(file, paths.getError(), null, null, null);
          }
        }
      }
    } catch (Exception ex) {
      errors++;
      log.error("❌ Erro ao acessar/processar pasta bancária {}: {}", bankKey, ex.getMessage(), ex);
    }

    return new BankProcessingResult(scanned, processed, duplicates, invalid, errors);
  }

  private FileResult validateAndProcess(String bankKey, Path file, FileProcessingProperties.FilePaths paths) {
    try (BufferedReader reader = Files.newBufferedReader(file, CNAB_CHARSET)) {
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

      moveFileService.moveNowBank(file, invalidDestination(paths), null, null, null);
      return FileResult.INVALID;
    } catch (DataIntegrityViolationException ex) {
      if (isAlreadyProcessedFile(ex)) {
        log.info("ℹ Arquivo bancário '{}' já foi processado anteriormente e não será importado novamente.",
          file.getFileName());

        if (Files.exists(file)) {
          moveFileService.moveNowBank(file, paths.getDuplicate(), null, null, null);
        } else {
          log.info("ℹ Arquivo bancário '{}' já foi movido pelo fluxo de duplicidade.", file.getFileName());
        }
        return FileResult.DUPLICATE;
      }

      return handleProcessingError(bankKey, file, paths, ex);
    } catch (Exception ex) {
      return handleProcessingError(bankKey, file, paths, ex);
    }
  }

  private FileResult handleProcessingError(
    String bankKey,
    Path file,
    FileProcessingProperties.FilePaths paths,
    Exception ex
  ) {
    log.error("❌ Não foi possível processar o arquivo bancário '{}' do banco {}. Motivo: {}",
      file.getFileName(), bankKey, friendlyErrorMessage(ex));
    log.debug("Detalhes técnicos do erro no arquivo bancário '{}'.", file.getFileName(), ex);

    if (Files.exists(file)) {
      moveFileService.moveNowBank(file, paths.getError(), null, null, null);
    } else {
      log.warn("⚠ Arquivo bancário '{}' já foi movido; não foi necessário enviá-lo novamente para a pasta de erro.",
        file.getFileName());
    }
    return FileResult.ERROR;
  }

  private boolean isAlreadyProcessedFile(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && (
        message.contains("uk_cs_processed_file_file_origin")
          || message.contains("Duplicate entry") && message.contains("cs_processed_file")
      )) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private String friendlyErrorMessage(Throwable throwable) {
    Throwable current = throwable;
    String lastMessage = null;

    while (current != null) {
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        lastMessage = current.getMessage();
      }
      current = current.getCause();
    }

    if (lastMessage == null || lastMessage.isBlank()) {
      return "ocorreu uma falha inesperada durante a importação";
    }
    return lastMessage;
  }

  private String invalidDestination(FileProcessingProperties.FilePaths paths) {
    if (paths.getInvalid() != null && !paths.getInvalid().isBlank()) return paths.getInvalid();
    if (paths.getError() != null && !paths.getError().isBlank()) return paths.getError();
    throw new IllegalStateException("Caminho invalid/error não configurado para processamento bancário.");
  }

  private enum FileResult {
    PROCESSED,
    DUPLICATE,
    INVALID,
    ERROR
  }

  private record BankProcessingResult(int scanned, int processed, int duplicates, int invalid, int errors) {
  }
}