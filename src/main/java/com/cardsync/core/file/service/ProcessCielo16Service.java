package com.cardsync.core.file.service;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.core.file.util.MoveFileService;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.*;
import com.cardsync.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Lê o arquivo CIELO16 (Pix) do Extrato Eletrônico Cielo v15.15.
 *
 * Registro "8" (Detalhe da Transação Pix, 400 bytes) — diferente do Registro E (cartão), já traz
 * captura E liquidação no mesmo registro (não existe um "CIELO16 de pagamento" separado pra casar
 * por chave), então mapeia direto pra {@link TransactionAcqEntity} (mesma entidade do CIELO03) sem
 * InstallmentAcqEntity/SalesSummaryEntity — o motivo que forçou isso no CIELO04 não se aplica aqui.
 *
 * Sem dado real de produção pra validar (este cliente nunca recebeu Pix pela Cielo — todo
 * histórico de CIELO16 é só Header+Trailer) — layout conferido contra o arquivo de teste oficial
 * da Cielo (ArquivoTeste_ExtratoEletronico/CIELO16D_...TXT, com Registros "8" reais).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCielo16Service {

  private static final Charset CIELO_CHARSET = Charset.forName("windows-1252");
  private static final String PIX_TRANSACTION_TYPE = "01";

  private final FileLookupService lookupService;
  private final MoveFileService moveFileService;
  private final TransactionAcqRepository transactionAcqRepository;
  private final ProcessedFileRepository processedFileRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths, String contentHash) {
    ProcessedFileEntity processedFile = null;
    try {
      log.info("▶ Iniciando processamento Cielo CIELO16: {}", file.getFileName());
      List<String> lines = Files.readAllLines(file, CIELO_CHARSET);
      processedFile = new ProcessedFileEntity();
      processedFile.setContentHash(contentHash);

      List<TransactionAcqEntity> transactions = new ArrayList<>();
      Map<String, Integer> transactionTypeCounts = new TreeMap<>();
      int recognized = 0;
      int ignored = 0;
      int warnings = 0;

      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        int lineNumber = i + 1;
        String recordType = FileParserUtils.extractStringLine(line, "0-1", lineNumber);
        if (recordType == null || recordType.isBlank()) {
          ignored++;
          continue;
        }

        switch (recordType) {
          case "0" -> {
            recognized++;
            processHeader(line, lineNumber, file, processedFile, lines.size());
          }
          case "9" -> recognized++;
          case "8" -> {
            recognized++;
            String transactionType = trim(FileParserUtils.extractStringLine(line, "11-13", lineNumber));
            transactionTypeCounts.merge(transactionType == null ? "?" : transactionType, 1, Integer::sum);
            if (!PIX_TRANSACTION_TYPE.equals(transactionType)) {
              ignored++;
              warnings++;
              processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
                "CIELO16_UNSUPPORTED_TRANSACTION_TYPE", "Tipo de transação Pix ainda não suportado: " + transactionType, line));
              continue;
            }
            transactions.add(buildTransaction(line, lineNumber, processedFile, transactionType));
          }
          default -> {
            ignored++;
            warnings++;
            processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
              "CIELO16_UNSUPPORTED_RECORD_TYPE", "Tipo de registro Cielo ainda não mapeado: " + recordType, line));
          }
        }
      }

      if (processedFile.getOriginFile() == null) {
        throw new IllegalStateException("Header (registro 0) não encontrado: " + file.getFileName());
      }

      processedFile.setProcessedLines(recognized);
      processedFile.setIgnoredLines(ignored);
      processedFile.setWarningLines(warnings);
      processedFile.setErrorLines(0);
      processedFile.markFinished(warnings > 0 ? FileStatusEnum.PROCESSED_WITH_WARNINGS : FileStatusEnum.PROCESSED,
        "linhas=" + lines.size()
          + ", reconhecidas=" + recognized
          + ", ignoradas=" + ignored
          + ", avisos=" + warnings
          + ", transacoes=" + transactions.size()
          + ", tiposTransacao=" + transactionTypeCounts);

      collectPvNumbers(processedFile, transactions);

      processedFileRepository.save(processedFile);
      transactionAcqRepository.saveAll(transactions);

      moveFileService.moveAfterCommit(file, paths.getProcessed(), processedFile.getDateFile());
      log.info("✅ CIELO16 {} finalizado: status={}, {}", file.getFileName(), processedFile.getStatus(), processedFile.getStatusMessage());
    } catch (DataIntegrityViolationException ex) {
      log.error("⚠ Arquivo CIELO16 {} já processado anteriormente.", file.getFileName());
      if (processedFile != null) processedFile.setStatus(FileStatusEnum.DUPLICATE);
      moveFileService.moveAfterRollback(file, paths.getDuplicate(), processedFile == null ? null : processedFile.getDateFile());
      throw ex;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar CIELO16 {}: {}", file.getFileName(), safeMessage(ex), ex);
      if (processedFile != null) {
        processedFile.setStatus(FileStatusEnum.ERROR);
        processedFile.setErrorMessage(safeMessage(ex));
      }
      moveFileService.moveAfterRollback(file, paths.getError(), processedFile == null ? null : processedFile.getDateFile());
      throw new IllegalStateException(ex);
    }
  }

  private void processHeader(String line, int lineNumber, Path file, ProcessedFileEntity processedFile, int totalLines) {
    processedFile.setOriginFile(lookupService.origin("CIELO"));
    processedFile.setGroup(FileGroupEnum.ADQ);
    processedFile.setStatus(FileStatusEnum.PROCESSING);
    processedFile.setDateImport(OffsetDateTime.now());
    processedFile.setDateProcessing(OffsetDateTime.now());
    processedFile.setStartedAt(OffsetDateTime.now());
    processedFile.setFile(file.getFileName().toString());
    processedFile.setDateFile(FileParserUtils.extractDateLine(line, "11-19", lineNumber));
    processedFile.setTypeFile("CIELO" + FileParserUtils.extractStringLine(line, "47-49", lineNumber));
    processedFile.setPvGroupNumber(FileParserUtils.extractIntegerLine(line, "1-11", lineNumber));
    processedFile.setTotalLines(totalLines);
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  TransactionAcqEntity buildTransaction(String line, int lineNumber, ProcessedFileEntity processedFile, String transactionType) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "1-11", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    BigDecimal grossValue = FileParserUtils.extractSignedMoneyLine(line, "73-87", lineNumber);
    BigDecimal discountValue = FileParserUtils.extractSignedMoneyLine(line, "87-101", lineNumber).abs();
    BigDecimal liquidValue = FileParserUtils.extractSignedMoneyLine(line, "101-115", lineNumber);

    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setLineNumber(lineNumber);
    tx.setRecordType(transactionType);
    tx.setProcessedFile(processedFile);
    tx.setAcquirer(acquirer);
    tx.setEstablishment(establishment);
    tx.setCompany(establishment != null ? establishment.getCompany() : null);
    tx.setTid(FileParserUtils.extractStringLine(line, "25-61", lineNumber));
    tx.setNsu(FileParserUtils.extractLongLine(line, "61-67", lineNumber));
    tx.setGrossValue(grossValue);
    tx.setLiquidValue(liquidValue);
    tx.setDiscountValue(discountValue);
    tx.setMdrRate(calculateRate(grossValue, discountValue));
    tx.setMachine(FileParserUtils.extractStringLine(line, "161-169", lineNumber));
    tx.setSaleDate(FileParserUtils.extractOffsetDateTimeLineYearFirst(line, lineNumber, "13-19", "19-25"));
    tx.setInstallment(1);
    tx.setModality(ModalityEnum.DIGITAL_WALLET.getCode());
    tx.setFirstInstallmentValue(BigDecimal.ZERO);
    tx.setOtherInstallmentsValue(BigDecimal.ZERO);
    tx.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    tx.setStatusTransaction(StatusTransactionEnum.PENDING);
    tx.setStatusTransactionReason(StatusTransactionReasonEnum.NULL);
    return tx;
  }

  private BigDecimal calculateRate(BigDecimal grossValue, BigDecimal discountValue) {
    if (grossValue == null || grossValue.signum() == 0 || discountValue == null) return BigDecimal.ZERO;
    return discountValue.multiply(BigDecimal.valueOf(100)).divide(grossValue, 6, java.math.RoundingMode.HALF_UP);
  }

  private void collectPvNumbers(ProcessedFileEntity processedFile, List<TransactionAcqEntity> transactions) {
    transactions.stream()
      .map(TransactionAcqEntity::getEstablishment)
      .filter(Objects::nonNull)
      .map(EstablishmentEntity::getPvNumber)
      .filter(Objects::nonNull)
      .forEach(processedFile.getPvNumbers()::add);
  }

  private AcquirerEntity safeAcquirer() {
    try {
      return lookupService.acquirerByIdentifier("CIELO");
    } catch (Exception ex) {
      log.debug("Adquirente Cielo não encontrada durante parsing CIELO16: {}", ex.getMessage());
      return null;
    }
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    if (pvNumber == null) return null;
    try {
      return lookupService.establishmentByPvNumber(pvNumber);
    } catch (Exception ex) {
      log.debug("Estabelecimento não encontrado para PV {} durante parsing CIELO16: {}", pvNumber, ex.getMessage());
      return null;
    }
  }

  private String trim(String value) {
    return value == null ? null : value.trim();
  }

  private String safeMessage(Exception ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
