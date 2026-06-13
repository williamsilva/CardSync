package com.cardsync.core.file.service;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.erp.calculator.InstallmentErpGenerator;
import com.cardsync.core.file.erp.calculator.TransactionErpFeeCalculator;
import com.cardsync.core.file.erp.dto.TransactionErpCsvDto;
import com.cardsync.core.file.erp.dto.TransactionErpCsvReader;
import com.cardsync.core.file.erp.mapper.TransactionErpMapper;
import com.cardsync.core.file.erp.resolver.CaptureResolver;
import com.cardsync.core.file.erp.resolver.ErpBusinessContextResolver;
import com.cardsync.core.file.erp.resolver.ModalityResolver;
import com.cardsync.core.file.erp.validator.TransactionErpValidationError;
import com.cardsync.core.file.erp.validator.TransactionErpValidationResult;
import com.cardsync.core.file.erp.validator.TransactionErpValidator;
import com.cardsync.core.file.util.FileHashService;
import com.cardsync.core.file.util.MoveFileService;
import com.cardsync.domain.model.OriginFileEntity;
import com.cardsync.domain.model.ProcessedFileEntity;
import com.cardsync.domain.model.ProcessedFileErrorEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;
import com.cardsync.domain.model.enums.ProcessedFileErrorTypeEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.repository.ProcessedFileRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFileErpService {

  private final TransactionErpMapper transactionErpMapper;
  private final TransactionErpCsvReader transactionErpCsvReader;
  private final TransactionErpValidator transactionErpValidator;
  private final InstallmentErpGenerator installmentErpGenerator;
  private final FileProcessingProperties fileProcessingProperties;
  private final ErpBusinessContextResolver erpBusinessContextResolver;
  private final TransactionErpFeeCalculator transactionErpFeeCalculator;

  private final FileLookupService lookupService;
  private final FileHashService fileHashService;
  private final MoveFileService moveFileService;

  private final ProcessedFileRepository processedFileRepository;
  private final TransactionErpRepository transactionErpRepository;

  public void processFiles() {
    var paths = fileProcessingProperties.getPathsOrThrow("erp");
    paths.ensureDirectories();
    int processed = 0;
    int errors = 0;

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(paths.getInput()), "*.csv")) {
      for (Path file : stream) {
        try {
          processFile(file, paths);
          processed++;
        } catch (Exception ex) {
          errors++;
          log.error("❌ Falha no processamento do arquivo ERP {}: {}", file.getFileName(), ex.getMessage(), ex);
          if (Files.exists(file)) {
            moveFileService.moveNow(file, paths.getError());
          }
        }
      }

      if (errors > 0) {
        log.warn("⚠ Processamento ERP finalizado com {} erro(s) e {} arquivo(s) processado(s). "
          + "Os arquivos com erro foram movidos para a pasta de erro; a esteira continua.", errors, processed);
      } else {
        log.info("✅ Processamento ERP finalizado. {} arquivo(s) processado(s).", processed);
      }
    } catch (Exception ex) {
      log.error("❌ Erro ao acessar/processar pasta ERP: {}", ex.getMessage(), ex);
      throw new IllegalStateException(ex);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths) {
    ProcessedFileEntity processedFile = null;
    try {
      var originFile = lookupService.origin("ERP");
      String contentHash = fileHashService.sha256(file);

      var duplicatedFile = processedFileRepository.findFirstByContentHash(contentHash)
        .or(() -> processedFileRepository.findFirstByFileAndOriginFile(file.getFileName().toString(), originFile));

      if (duplicatedFile.isPresent()) {
        ProcessedFileEntity original = duplicatedFile.get();
        log.warn(
          "⚠ Arquivo ERP duplicado ignorado: nome={}, arquivoOriginal={}, dataArquivo={}, sha256={}",
          file.getFileName(),
          original.getFile(),
          original.getDateFile(),
          contentHash
        );
        moveFileService.moveNow(file, paths.getDuplicate(), original.getDateFile());
        return;
      }

      List<TransactionErpCsvDto> rows = transactionErpCsvReader.read(file);
      processedFile = createProcessedFile(file, rows, originFile, contentHash);
      processedFile.markProcessing();
      processedFile.setTotalLines(transactionErpCsvReader.countPhysicalLines(file));
      processedFileRepository.save(processedFile);

      if (rows.isEmpty()) {
        processedFile.setErrorLines(1);
        processedFile.setIgnoredLines(1);
        processedFile.setErrorMessage("Arquivo ERP vazio ou sem cabeçalho válido.");
        processedFile.addError(ProcessedFileErrorEntity.of(
          1,
          ProcessedFileErrorTypeEnum.HEADER,
          "ERP_EMPTY_OR_INVALID_HEADER",
          "Arquivo ERP vazio ou sem cabeçalho válido.",
          null
        ));
        finishFile(processedFile, FileStatusEnum.INVALID, "Arquivo inválido: sem linhas importáveis.");
        moveFileService.moveAfterCommit(file, paths.getError(), processedFile.getDateFile());
        return;
      }

      List<TransactionErpEntity> transactions = new ArrayList<>();
      int ignored = 0;
      int warnings = 0;
      int errors = 0;
      int pendingContract = 0;
      int pendingBusinessContext = 0;
      for (TransactionErpCsvDto row : rows) {
        int lineNumber = row.getLineNumber() != null ? row.getLineNumber() : 2;
        TransactionErpValidationResult validation = transactionErpValidator.validate(row);
        boolean lineHasWarnings = false;

        if (!validation.warnings().isEmpty()) {
          lineHasWarnings = true;
          addErrors(processedFile, lineNumber, validation.warnings(), null);
        }

        if (!transactionErpValidator.isPaymentType(row != null ? row.getTransaction() : null)) {
          ignored++;
          if (lineHasWarnings) warnings++;
          continue;
        }

        if (!validation.isValid()) {
          ignored++;
          errors++;
          addErrors(processedFile, lineNumber, validation.errors(), null);
          if (lineHasWarnings) warnings++;
          continue;
        }

        try {
          RowProcessingResult result = buildTransaction(row, processedFile, lineNumber);
          transactions.add(result.transaction());
          if (result.businessContextIncomplete()) {
            pendingBusinessContext++;
            lineHasWarnings = true;
            processedFile.addError(ProcessedFileErrorEntity.of(
              lineNumber,
              ProcessedFileErrorTypeEnum.LOOKUP,
              "ERP_BUSINESS_CONTEXT_INCOMPLETE",
              "Empresa ou estabelecimento não foi resolvido completamente. Quando o layout ERP/TEF não envia esses campos, a venda fica pendente para revisão/reprocessamento comercial.",
              null
            ));
          } else if (!result.contractFound()) {
            pendingContract++;
            lineHasWarnings = true;
            processedFile.addError(ProcessedFileErrorEntity.of(
              lineNumber,
              ProcessedFileErrorTypeEnum.CONTRACT,
              "ERP_CONTRACT_NOT_FOUND",
              "Contrato/taxa vigente não encontrado para a transação. A venda foi importada com taxa contratada zerada para revisão.",
              null
            ));
          }
          if (lineHasWarnings) warnings++;
        } catch (Exception ex) {
          ignored++;
          errors++;
          processedFile.addError(ProcessedFileErrorEntity.of(
            lineNumber,
            ProcessedFileErrorTypeEnum.LOOKUP,
            "ERP_ROW_PROCESSING_ERROR",
            safeMessage(ex),
            null
          ));
        }

      }

      processedFile.setProcessedLines(transactions.size());
      processedFile.setIgnoredLines(ignored);
      processedFile.setWarningLines(warnings);
      processedFile.setErrorLines(errors);
      processedFile.setPendingContractLines(pendingContract);
      processedFile.setPendingBusinessContextLines(pendingBusinessContext);
      transactionErpRepository.saveAll(transactions);

      FileStatusEnum finalStatus = resolveFinalStatus(transactions.size(), ignored, warnings, errors);
      String message = buildSummaryMessage(processedFile);
      if (finalStatus == FileStatusEnum.INVALID || finalStatus == FileStatusEnum.ERROR) {
        processedFile.setErrorMessage(message);
      }
      finishFile(processedFile, finalStatus, message);

      if (finalStatus == FileStatusEnum.INVALID || finalStatus == FileStatusEnum.ERROR) {
        moveFileService.moveAfterCommit(file, paths.getError(), processedFile.getDateFile());
      } else {
        moveFileService.moveAfterCommit(file, paths.getProcessed(), processedFile.getDateFile());
      }

      log.info("✅ Arquivo ERP {} finalizado. status={}, {}", file.getFileName(), finalStatus, message);
      if (pendingContract > 0 || pendingBusinessContext > 0) {
        log.info("📌 Arquivo ERP {} importado com pendências comerciais: contratosPendentes={}, contextoComercialPendente={}",
          file.getFileName(), pendingContract, pendingBusinessContext);
      }
    } catch (DataIntegrityViolationException ex) {
      // Proteção para concorrência: outro processo pode registrar o mesmo arquivo
      // entre a consulta preventiva e o insert. Duplicidade é uma situação esperada,
      // portanto não deve gerar stack trace de erro no console.
      log.warn("⚠ Arquivo ERP {} já foi registrado por outro processamento e será movido para duplicados.", file.getFileName());
      moveFileService.moveAfterRollback(file, paths.getDuplicate(), processedFile == null ? null : processedFile.getDateFile());
    } catch (Exception ex) {
      log.error("❌ Erro ao processar arquivo ERP {}: {}", file.getFileName(), ex.getMessage(), ex);
      if (processedFile != null) {
        processedFile.setStatus(FileStatusEnum.ERROR);
        processedFile.setErrorMessage(safeMessage(ex));
      }
      moveFileService.moveAfterRollback(file, paths.getError(), processedFile == null ? null : processedFile.getDateFile());
      throw new IllegalStateException(ex);
    }
  }

  private ProcessedFileEntity createProcessedFile(
    Path file, List<TransactionErpCsvDto> rows, OriginFileEntity originFile, String contentHash) {

    ProcessedFileEntity processedFile = new ProcessedFileEntity();
    processedFile.setOriginFile(originFile);
    processedFile.setContentHash(contentHash);
    processedFile.setGroup(FileGroupEnum.ERP);
    processedFile.setStatus(FileStatusEnum.RECEIVED);
    processedFile.setDateImport(OffsetDateTime.now());
    processedFile.setDateProcessing(OffsetDateTime.now());
    processedFile.setFile(file.getFileName().toString());
    processedFile.setDateFile(resolveDateFile(rows));
    processedFile.setTypeFile("Relatório de transações TEF");
    processedFile.setVersion("MultiVendas TEF");

    if (fileProcessingProperties.getErp() != null) {
      processedFile.setCommercialName(fileProcessingProperties.getErp().getDefaultCommercialName());
      processedFile.setPvGroupNumber(fileProcessingProperties.getErp().getDefaultPvGroupNumber());
    }
    return processedFile;
  }

  private RowProcessingResult buildTransaction(TransactionErpCsvDto row, ProcessedFileEntity processedFile, int lineNumber) {
    TransactionErpEntity tx = transactionErpMapper.map(row);
    tx.setProcessedFile(processedFile);
    tx.setLineNumber(lineNumber);
    tx.setStatusTransaction(StatusTransactionEnum.PENDING.getCode());

    var acquirer = lookupService.acquirerByIdentifier(row.getAcquirer());
    var flag = lookupService.flagByName(row.getFlag());
    tx.setAcquirer(acquirer);
    tx.setFlag(flag);
    tx.setModality(ModalityResolver.resolve(row.getModality(), tx.getInstallment()).getCode());
    tx.setCapture(CaptureResolver.resolve(row.getOrigin(), row.getTransaction()).getCode());
    erpBusinessContextResolver.resolve(row, tx);

    var feeResult = transactionErpFeeCalculator.calculateContractedFee(tx);
    boolean contextIncomplete = tx.getCompany() == null || tx.getEstablishment() == null;
    applyCommercialPendingRule(tx, feeResult.contractFound(), contextIncomplete);
    installmentErpGenerator.generate(tx, feeResult.paymentTermDays()).forEach(tx::addInstallment);
    return new RowProcessingResult(tx, feeResult.contractFound(), contextIncomplete);
  }

  private void applyCommercialPendingRule(TransactionErpEntity tx, boolean contractFound, boolean contextIncomplete) {
    /*
     * Prioridade CardSync:
     * 1) primeiro resolver contexto comercial (empresa/PV), porque sem isso o contrato correto
     *    nem sempre pode ser localizado;
     * 2) somente com contexto resolvido marcar pendência de contrato/taxa;
     * 3) quando tudo estiver resolvido, status OK.
     */
    if (contextIncomplete) {
      ErpCommercialStatusEnum status;
      if (tx.getCompany() == null && tx.getEstablishment() == null) {
        status = ErpCommercialStatusEnum.PENDING_BUSINESS_CONTEXT;
      } else if (tx.getCompany() == null) {
        status = ErpCommercialStatusEnum.PENDING_COMPANY;
      } else {
        status = ErpCommercialStatusEnum.PENDING_ESTABLISHMENT;
      }

      tx.setCommercialStatus(status);
      tx.setCommercialStatusMessage("Venda importada, mas pendente de vínculo comercial. Revise CNPJ da empresa " +
        "e PV/estabelecimento informados no arquivo ERP.");
      tx.setStatusTransaction(StatusTransactionEnum.PENDING.getCode());
      return;
    }

    if (!contractFound) {
      tx.setMissingContractAtSale(Boolean.TRUE);
      tx.setCommercialStatus(ErpCommercialStatusEnum.PENDING_CONTRACT);
      tx.setCommercialStatusMessage("Venda importada sem contrato/taxa vigente na data da transação. Na conciliação " +
        "de taxa, se houver venda correspondente na adquirente, a taxa real da adquirente será assumida para evitar falsa divergência.");
      tx.setStatusTransaction(StatusTransactionEnum.PENDING.getCode());
      return;
    }

    tx.setMissingContractAtSale(Boolean.FALSE);
    tx.setCommercialStatus(ErpCommercialStatusEnum.OK);
    tx.setCommercialStatusMessage("Venda importada com contexto comercial e contrato vigente resolvidos.");
  }

  private void addErrors(ProcessedFileEntity processedFile, int lineNumber, List<TransactionErpValidationError> errors, String rawLine) {
    for (TransactionErpValidationError error : errors) {
      processedFile.addError(ProcessedFileErrorEntity.of(
        lineNumber,
        error.type(),
        error.code(),
        error.message(),
        rawLine
      ));
    }
  }

  private FileStatusEnum resolveFinalStatus(int processed, int ignored, int warnings, int errors) {
    if (processed == 0 && errors > 0) return FileStatusEnum.INVALID;
    if (processed == 0 && ignored > 0) return FileStatusEnum.PROCESSED_WITH_WARNINGS;
    if (errors > 0 || warnings > 0 || ignored > 0) return FileStatusEnum.PROCESSED_WITH_WARNINGS;
    return FileStatusEnum.PROCESSED;
  }

  private String buildSummaryMessage(ProcessedFileEntity processedFile) {
    return "linhas=" + processedFile.getTotalLines()
      + ", importadas=" + processedFile.getProcessedLines()
      + ", ignoradas=" + processedFile.getIgnoredLines()
      + ", avisos=" + processedFile.getWarningLines()
      + ", erros=" + processedFile.getErrorLines()
      + ", pendentesContrato=" + processedFile.getPendingContractLines()
      + ", pendentesContextoComercial=" + processedFile.getPendingBusinessContextLines();
  }

  private void finishFile(ProcessedFileEntity processedFile, FileStatusEnum status, String message) {
    processedFile.markFinished(status, message);
    processedFileRepository.save(processedFile);
  }

  private String safeMessage(Exception ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  private LocalDate resolveDateFile(List<TransactionErpCsvDto> rows) {
    return rows.stream()
      .map(TransactionErpCsvDto::getSaleDate)
      .filter(Objects::nonNull)
      .min(Comparator.naturalOrder())
      .map(OffsetDateTime::toLocalDate)
      .orElse(LocalDate.now());
  }

  private record RowProcessingResult(
    TransactionErpEntity transaction,
    boolean contractFound,
    boolean businessContextIncomplete
  ) {
  }
}