package com.cardsync.core.file.service;

import com.cardsync.core.file.bank.BankingDomicileResolver;
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
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRedeEeVcService {
  private static final int STATUS_PENDING = 1;
  private static final int REQUEST_AWAITING_DECISION = 1;
  private static final Charset REDE_CHARSET = Charset.forName("ISO-8859-1");

  private static final Set<String> SUPPORTED_IDENTIFIERS = Set.of(
    "002", "004", "005", "006", "008", "010", "011", "012", "014", "016", "017", "018", "019", "020", "021",
    "022", "024", "026", "028", "029", "033", "034", "035", "036", "040");

  private final FileLookupService lookupService;
  private final MoveFileService moveFileService;
  private final AdjustmentTransactionLinkService adjustmentTransactionLinkService;

  private final AdjustmentRepository adjustmentRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final ProcessedFileRepository processedFileRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final PvMatrixHeaderRepository pvMatrixHeaderRepository;
  private final ArchiveTrailerRepository archiveTrailerRepository;
  private final RequestNoticeRepository redeRequestNoticeRepository;
  private final TotalizerMatrixRepository totalizerMatrixRepository;
  private final SerasaConsultationRepository serasaConsultationRepository;
  private final RedeIcPlusTransactionRepository redeIcPlusTransactionRepository;

  private final BankingDomicileResolver bankingDomicileResolver;
  private final ProcessedFilePvService processedFilePvService;
  private final ThreadLocal<RedeProcessingWarningCollector> warningCollector = new ThreadLocal<>();

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths, String contentHash) {
    ProcessedFileEntity processedFile = null;
    try {
      warningCollector.set(new RedeProcessingWarningCollector("EEVC"));
      log.info("▶ Iniciando processamento Rede EEVC: {}", file.getFileName());
      List<String> lines = Files.readAllLines(file, REDE_CHARSET);
      processedFile = new ProcessedFileEntity();
      processedFile.setContentHash(contentHash);

      List<PvMatrixHeaderEntity> pvMatrixHeaders = new ArrayList<>();
      List<SalesSummaryEntity> summaries = new ArrayList<>();
      List<TransactionAcqEntity> transactions = new ArrayList<>();
      List<TransactionAcqEntity> ecommerceComplements = new ArrayList<>();
      List<InstallmentAcqEntity> installments = new ArrayList<>();
      List<RequestNoticeEntity> requestNotices = new ArrayList<>();
      List<AdjustmentEntity> adjustments = new ArrayList<>();
      List<TotalizerMatrixEntity> totalizerMatrices = new ArrayList<>();
      List<ArchiveTrailerEntity> archiveTrailers = new ArrayList<>();
      List<EeVcRvInstallment> layoutInstallments = new ArrayList<>();
      List<RedeIcPlusTransactionEntity> icPlusTransactions = new ArrayList<>();
      List<SerasaConsultationEntity> auxiliaryConsultations = new ArrayList<>();
      Map<String, Integer> countsByIdentifier = new TreeMap<>();
      Set<String> unidentified = new TreeSet<>();

      int recognized = 0;
      int ignored = 0;
      int warnings = 0;

      for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        int lineNumber = i + 1;
        String identifier = trim(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
        if (identifier == null || identifier.isBlank()) {
          ignored++;
          continue;
        }
        if (!SUPPORTED_IDENTIFIERS.contains(identifier)) {
          ignored++;
          warnings++;
          unidentified.add(identifier);
          processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
            "REDE_EEVC_UNSUPPORTED_IDENTIFIER", "Identificador EEVC não mapeado: " + identifier, line));
          continue;
        }

        recognized++;
        countsByIdentifier.merge(identifier, 1, Integer::sum);
        switch (identifier) {
          case "002" -> processHeader(line, lineNumber, file, processedFile, lines.size());
          case "004" -> pvMatrixHeaders.add(buildPvMatrixHeader(line, lineNumber, processedFile));
          case "005" -> requestNotices.add(buildRequestNotice(line, lineNumber, processedFile, summaries, false));
          case "006" -> summaries.add(buildSalesSummary(line, lineNumber, processedFile, TransactionLayout.ROTATIVO));
          case "008" -> addIfValid(transactions, buildTransaction(line, lineNumber, processedFile, summaries, TransactionLayout.ROTATIVO));
          case "010" -> summaries.add(buildSalesSummary(line, lineNumber, processedFile, TransactionLayout.PARCELADO));
          case "011" -> adjustments.add(buildAdjustmentCvNsuCredit(line, lineNumber, processedFile, summaries));
          case "012" -> addIfValid(transactions, buildTransaction(line, lineNumber, processedFile, summaries, TransactionLayout.PARCELADO));
          case "014", "020" -> layoutInstallments.add(buildRvInstallment(line, lineNumber));
          case "016" -> summaries.add(buildSalesSummary(line, lineNumber, processedFile, TransactionLayout.IATA));
          case "017", "019", "021" -> auxiliaryConsultations.add(buildAuxiliaryConsultation(line, lineNumber, processedFile));
          case "018" -> addIfValid(transactions, buildTransaction(line, lineNumber, processedFile, summaries, TransactionLayout.IATA));
          case "022" -> summaries.add(buildSalesSummary(line, lineNumber, processedFile, TransactionLayout.DOLLAR));
          case "024" -> addIfValid(transactions, buildTransaction(line, lineNumber, processedFile, summaries, TransactionLayout.DOLLAR));
          case "026" -> totalizerMatrices.add(buildTotalizerMatrix(line, lineNumber, processedFile));
          case "028" -> archiveTrailers.add(buildArchiveTrailer(line, lineNumber, processedFile));
          case "029" -> icPlusTransactions.add(buildIcPlusTransaction(line, lineNumber, processedFile, summaries));
          case "033" -> requestNotices.add(buildRequestNotice(line, lineNumber, processedFile, summaries, true));
          case "034", "035", "036" -> addIfValid(
            ecommerceComplements,
            buildEcommerceTransaction(line, lineNumber, processedFile, summaries)
          );
          case "040" -> auxiliaryConsultations.add(buildRechargeAsAuxiliaryConsultation(line, lineNumber, processedFile));
          default -> {
            ignored++;
            warnings++;
            unidentified.add(identifier);
          }
        }
      }

      if (processedFile.getOriginFile() == null) {
        throw new IllegalStateException("Header 002 não encontrado: " + file.getFileName());
      }

      int ignoredEcommerceComplements = mergeEcommerceComplements(transactions, ecommerceComplements);
      ignored += ignoredEcommerceComplements;

      if (ignoredEcommerceComplements > 0) {
        log.info(
          "ℹ Rede EEVC: {} complemento(s) 034/035/036 ignorado(s) por não possuir transação principal equivalente no arquivo {}",
          ignoredEcommerceComplements,
          file.getFileName()
        );
      }

      installments.addAll(buildInstallments(transactions, layoutInstallments));

      RedeProcessingWarningCollector collector = warningCollector.get();
      if (collector != null && collector.hasWarnings()) {
        warnings += collector.totalWarnings();
        collector.addProcessedFileErrors(processedFile, "REDE_EEVC_MONETARY_OUT_OF_RANGE");
      }

      ensureSalesSummariesBankingDomicile(summaries);

      int alreadyPersisted = removeAlreadyPersisted(transactions);
      if (alreadyPersisted > 0) {
        ignored += alreadyPersisted;
        log.info(
          "ℹ Rede EEVC: {} transação(ões) já existente(s) no banco ignorada(s) (reprocessamento) no arquivo {}",
          alreadyPersisted,
          file.getFileName()
        );
      }

      processedFile.setProcessedLines(recognized);
      processedFile.setIgnoredLines(ignored);
      processedFile.setWarningLines(warnings);
      processedFile.setErrorLines(0);
      processedFile.markFinished(warnings > 0 ? FileStatusEnum.PROCESSED_WITH_WARNINGS : FileStatusEnum.PROCESSED,
        "linhas=" + lines.size()
          + ", reconhecidas=" + recognized
          + ", ignoradas=" + ignored
          + ", summaries=" + summaries.size()
          + ", transactions=" + transactions.size()
          + ", parcelas=" + installments.size()
          + ", parcelasLayout=" + layoutInstallments.size()
          + ", requests=" + requestNotices.size()
          + ", adjustments=" + adjustments.size()
          + ", totalizers=" + totalizerMatrices.size()
          + ", trailers=" + archiveTrailers.size()
          + ", icPlus=" + icPlusTransactions.size()
          + ", auxiliares=" + auxiliaryConsultations.size()
          + ", avisosLayout=" + (collector == null ? 0 : collector.totalWarnings())
          + ", ids=" + countsByIdentifier
          + ", unidentified=" + unidentified);

      // Registra os PVs declarados nos cabeçalhos de matriz (registro 004) antes de salvar,
      // garantindo que arquivos sem movimento (zero resumos de vendas) ainda marquem seus
      // PVs como "presentes" no calendário. Arquivos totalmente zerados vêm sem o registro
      // 004; nesses casos o PV grupo do header (registro 002) é a única fonte disponível.
      pvMatrixHeaders.stream()
          .map(PvMatrixHeaderEntity::getPvNumber)
          .filter(java.util.Objects::nonNull)
          .forEach(processedFile.getPvNumbers()::add);
      if (processedFile.getPvGroupNumber() != null) {
        processedFile.getPvNumbers().add(processedFile.getPvGroupNumber());
      }

      processedFileRepository.save(processedFile);
      pvMatrixHeaderRepository.saveAll(pvMatrixHeaders);
      salesSummaryRepository.saveAll(summaries);
      processedFilePvService.collectFromSalesSummary(processedFile);
      transactionAcqRepository.saveAll(transactions);
      installmentAcqRepository.saveAll(installments);
      redeRequestNoticeRepository.saveAll(requestNotices);
      List<AdjustmentEntity> savedAdjustments = adjustmentRepository.saveAll(adjustments);
      AdjustmentTransactionLinkService.LinkResult adjustmentLinkResult =
        adjustmentTransactionLinkService.linkSavedAdjustments(savedAdjustments);
      log.info(
        "🔗 Vínculo de ajustes EEVC com transações: analisados={}, vinculados={}",
        adjustmentLinkResult.analyzed(),
        adjustmentLinkResult.linked()
      );
      totalizerMatrixRepository.saveAll(totalizerMatrices);
      archiveTrailerRepository.saveAll(archiveTrailers);
      redeIcPlusTransactionRepository.saveAll(icPlusTransactions);
      serasaConsultationRepository.saveAll(auxiliaryConsultations);

      if (!unidentified.isEmpty()) {
        log.warn("⚠ EEVC {} possui identificadores não mapeados: {}", file.getFileName(), unidentified);
      }

      moveFileService.moveAfterCommit(file, paths.getProcessed(), processedFile.getDateFile());
      if (collector != null) {
        collector.logSummary(log, file.getFileName().toString());
      }
      log.info("✅ EEVC {} finalizado: status={}, {}", file.getFileName(), processedFile.getStatus(), processedFile.getStatusMessage());
    } catch (DataIntegrityViolationException ex) {
      log.error("⚠ Arquivo EEVC {} já processado anteriormente.", file.getFileName());
      if (processedFile != null) processedFile.setStatus(FileStatusEnum.DUPLICATE);
      moveFileService.moveAfterRollback(file, paths.getDuplicate(), processedFile == null ? null : processedFile.getDateFile());
      throw ex;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar EEVC {}: {}", file.getFileName(), safeMessage(ex), ex);
      if (processedFile != null) {
        processedFile.setStatus(FileStatusEnum.ERROR);
        processedFile.setErrorMessage(safeMessage(ex));
      }
      moveFileService.moveAfterRollback(file, paths.getError(), processedFile == null ? null : processedFile.getDateFile());
      throw new IllegalStateException(ex);
    } finally {
      warningCollector.remove();
    }
  }

  private void processHeader(String line, int lineNumber, Path file, ProcessedFileEntity processedFile, int totalLines) {
    processedFile.setOriginFile(lookupService.origin("REDE"));
    processedFile.setGroup(FileGroupEnum.ADQ);
    processedFile.setStatus(FileStatusEnum.PROCESSING);
    processedFile.setDateImport(OffsetDateTime.now());
    processedFile.setDateProcessing(OffsetDateTime.now());
    processedFile.setStartedAt(OffsetDateTime.now());
    processedFile.setFile(file.getFileName().toString());
    processedFile.setDateFile(FileParserUtils.extractDateLine(line, "3-11", lineNumber));
    processedFile.setTypeFile(FileParserUtils.extractStringLine(line, "19-49", lineNumber));
    processedFile.setCommercialName(FileParserUtils.extractStringLine(line, "49-71", lineNumber));
    processedFile.setPvGroupNumber(FileParserUtils.extractIntegerLine(line, "77-86", lineNumber));
    processedFile.setVersion(FileParserUtils.extractStringLine(line, "101-121", lineNumber));
    processedFile.setTotalLines(totalLines);
  }

  private PvMatrixHeaderEntity buildPvMatrixHeader(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    PvMatrixHeaderEntity header = new PvMatrixHeaderEntity();
    header.setLineNumber(lineNumber);
    header.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    header.setPvNumber(pvNumber);
    header.setCommercialName(FileParserUtils.extractStringLine(line, "12-34", lineNumber));
    header.setProcessedFile(processedFile);
    header.setAcquirer(acquirer);
    header.setEstablishment(establishment);
    header.setCompany(establishment != null ? establishment.getCompany() : null);
    return header;
  }

  private SalesSummaryEntity buildSalesSummary(String line, int lineNumber, ProcessedFileEntity processedFile, TransactionLayout layout) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    Integer agency = FileParserUtils.extractIntegerLine(line, "24-29", lineNumber);
    Integer currentAccount = FileParserUtils.extractIntegerLine(line, "29-40", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String acquirerCode = trim(FileParserUtils.extractStringLine(line, "136-137", lineNumber));

    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setPvNumber(pvNumber);
    summary.setAcquirer(acquirer);
    summary.setCompany(establishment != null ? establishment.getCompany() : null);
    summary.setFlag(safeFlag(acquirer, acquirerCode));
    summary.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    summary.setSummaryType(layout.summaryType);
    summary.setLineNumber(lineNumber);
    summary.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    summary.setCreditOrderStatus(StatusReconciliationEnum.PENDING);
    summary.setTransactionsStatus(StatusTransactionEnum.PENDING);
    summary.setRvNumber(FileParserUtils.extractIntegerLine(line, "12-21", lineNumber));
    summary.setBank(FileParserUtils.extractStringLine(line, "21-24", lineNumber));
    summary.setAgency(agency);
    summary.setCurrentAccount(currentAccount);
    summary.setRvDate(FileParserUtils.extractDateLine(line, "40-48", lineNumber));
    summary.setNumberCvNsu(FileParserUtils.extractIntegerLine(line, "48-53", lineNumber));
    summary.setGrossValue(safeMoney(line, "53-68", lineNumber, "gross_value", "sales_summary"));
    summary.setTipValue(safeMoney(line, "68-83", lineNumber, layout == TransactionLayout.IATA ? "boarding_fee_value" : "tip_value", "sales_summary"));
    summary.setRejectedValue(safeMoney(line, "83-98", lineNumber, "rejected_value", "sales_summary"));
    summary.setDiscountValue(safeMoney(line, "98-113", lineNumber, "discount_value", "sales_summary"));
    summary.setLiquidValue(safeMoney(line, "113-128", lineNumber, "liquid_value", "sales_summary"));
    summary.setFirstInstallmentCreditDate(FileParserUtils.extractDateLine(line, "128-136", lineNumber));
    if (layout.summaryModality != null) summary.setModality(layout.summaryModality);
    applyBankingDomicile(summary, safeBankingDomicile(summary.getBank(), establishment, agency, currentAccount));
    summary.setProcessedFile(processedFile);
    return summary;
  }

  private TransactionAcqEntity buildTransaction(String line, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries, TransactionLayout layout) {
    String recordType = trim(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    Integer rvNumber = FileParserUtils.extractIntegerLine(line, "12-21", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    SalesSummaryEntity summary = findSummary(summaries, pvNumber, rvNumber);

    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setRecordType(recordType);
    tx.setLineNumber(lineNumber);
    tx.setCompany(establishment != null ? establishment.getCompany() : null);
    tx.setEstablishment(establishment);
    tx.setAcquirer(acquirer);
    tx.setProcessedFile(processedFile);
    tx.setSalesSummary(summary);
    tx.setRvNumber(rvNumber);
    tx.setStatusAudit(STATUS_PENDING);
    tx.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    tx.setStatusTransaction(StatusTransactionEnum.PENDING);
    tx.setStatusTransactionReason(StatusTransactionReasonEnum.NULL);

    if (layout == TransactionLayout.DOLLAR) {
      return fillDollarTransaction(tx, line, lineNumber, acquirer);
    }

    boolean installment = layout == TransactionLayout.PARCELADO || layout == TransactionLayout.IATA;
    BigDecimal grossValue = safeMoney(line, "37-52", lineNumber, "gross_value", "transaction_acq");
    BigDecimal discountValue = safeMoney(line, installment ? "113-128" : "111-126", lineNumber, "discount_value", "transaction_acq");
    String acquirerCode = trim(FileParserUtils.extractStringLine(line, installment ? "261-262" : "229-230", lineNumber));

    tx.setFlag(safeFlag(acquirer, acquirerCode));
    tx.setGrossValue(grossValue);
    tx.setTipValue(safeMoney(line, "52-67", lineNumber, layout == TransactionLayout.IATA ? "boarding_fee_value" : "tip_value", "transaction_acq"));
    tx.setCardNumber(FileParserUtils.extractStringLine(line, "67-83", lineNumber));
    tx.setStatusCv(FileParserUtils.extractStringLine(line, "83-86", lineNumber));
    tx.setNsu(FileParserUtils.extractLongLine(line, installment ? "88-100" : "86-98", lineNumber));
    tx.setReferenceNumber(FileParserUtils.extractStringLine(line, installment ? "100-113" : "98-111", lineNumber));
    tx.setAuthorization(FileParserUtils.extractStringLine(line, installment ? "128-134" : "126-132", lineNumber));
    tx.setSaleDate(FileParserUtils.extractOffsetDateTimeLine(line, lineNumber, "21-29", installment ? "134-140" : "132-138"));
    tx.setDiscountValue(discountValue);
    tx.setLiquidValue(safeMoney(line, installment ? "205-220" : "203-218", lineNumber, "liquid_value", "transaction_acq"));
    tx.setMachine(FileParserUtils.extractStringLine(line, installment ? "250-258" : "218-226", lineNumber));
    tx.setFlexRate(safeMoney(line, installment ? "262-277" : "230-245", lineNumber, "flex_rate", "transaction_acq"));
    tx.setMdrRate(calculateRate(grossValue, discountValue));
    tx.setCapture(parseCapture(line, lineNumber, installment ? "204-205" : "202-203"));
    tx.setServiceCode(FileParserUtils.extractStringLine(line, installment ? "292-295" : "260-263", lineNumber));

    if (installment) {
      Integer installments = FileParserUtils.extractIntegerLine(line, "86-88", lineNumber);
      tx.setInstallment(normalizeInstallmentCount(installments));
      tx.setModality(resolveInstallmentModalityCode(tx.getInstallment()));
      tx.setFirstInstallmentValue(safeMoney(line, "220-235", lineNumber, "first_installment_value", "transaction_acq"));
      tx.setOtherInstallmentsValue(safeMoney(line, "235-250", lineNumber, "other_installments_value", "transaction_acq"));
    } else {
      tx.setInstallment(1);
      tx.setModality(ModalityEnum.CASH_CREDIT.getCode());
      tx.setFirstInstallmentValue(BigDecimal.ZERO);
      tx.setOtherInstallmentsValue(BigDecimal.ZERO);
      tx.setDccCurrency(FileParserUtils.extractStringLine(line, "29-37", lineNumber));
    }

    if (summary != null && summary.getModality() == null) {
      summary.setModality(tx.getModality());
    }
    return tx;
  }

  private TransactionAcqEntity fillDollarTransaction(TransactionAcqEntity tx, String line, int lineNumber, AcquirerEntity acquirer) {
    BigDecimal grossValue = safeMoney(line, "37-52", lineNumber, "gross_value_dollar", "transaction_acq");
    BigDecimal discountValue = safeMoney(line, "128-143", lineNumber, "discount_value", "transaction_acq");
    String acquirerCode = trim(FileParserUtils.extractStringLine(line, "168-169", lineNumber));

    tx.setFlag(safeFlag(acquirer, acquirerCode));
    tx.setGrossValue(grossValue);
    tx.setTipValue(safeMoney(line, "52-67", lineNumber, "tip_value", "transaction_acq"));
    tx.setCardNumber(FileParserUtils.extractStringLine(line, "67-83", lineNumber));
    tx.setStatusCv(FileParserUtils.extractStringLine(line, "83-86", lineNumber));
    tx.setNsu(FileParserUtils.extractLongLine(line, "103-115", lineNumber));
    tx.setReferenceNumber(FileParserUtils.extractStringLine(line, "115-128", lineNumber));
    tx.setAuthorization(FileParserUtils.extractStringLine(line, "143-149", lineNumber));
    tx.setSaleDate(FileParserUtils.extractOffsetDateTimeLine(line, lineNumber, "21-29", "149-155"));
    tx.setDiscountValue(discountValue);
    tx.setLiquidValue(zero(grossValue).subtract(zero(discountValue)));
    tx.setMachine(FileParserUtils.extractStringLine(line, "155-163", lineNumber));
    tx.setMdrRate(calculateRate(grossValue, discountValue));
    tx.setCapture(parseCapture(line, lineNumber, "163-165"));
    tx.setServiceCode(FileParserUtils.extractStringLine(line, "169-172", lineNumber));
    tx.setInstallment(1);
    tx.setModality(ModalityEnum.CASH_CREDIT.getCode());
    tx.setFirstInstallmentValue(BigDecimal.ZERO);
    tx.setOtherInstallmentsValue(BigDecimal.ZERO);
    tx.setDccCurrency(FileParserUtils.extractStringLine(line, "86-103", lineNumber));
    return tx;
  }

  private TransactionAcqEntity buildEcommerceTransaction(String line, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    String recordType = trim(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    Integer rvNumber = FileParserUtils.extractIntegerLine(line, "12-21", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    SalesSummaryEntity summary = findSummary(summaries, pvNumber, rvNumber);
    BigDecimal grossValue = safeMoney(line, "29-44", lineNumber, "gross_value", "transaction_acq_ecommerce");

    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setRecordType(recordType);
    tx.setLineNumber(lineNumber);
    tx.setCompany(establishment != null ? establishment.getCompany() : null);
    tx.setEstablishment(establishment);
    tx.setAcquirer(acquirer);
    tx.setFlag(summary != null ? summary.getFlag() : null);
    tx.setProcessedFile(processedFile);
    tx.setSalesSummary(summary);
    tx.setRvNumber(rvNumber);
    tx.setGrossValue(grossValue);
    tx.setTipValue(BigDecimal.ZERO);
    tx.setCardNumber(FileParserUtils.extractStringLine(line, "44-60", lineNumber));
    tx.setNsu(FileParserUtils.extractLongLine(line, "60-72", lineNumber));
    tx.setAuthorization(FileParserUtils.extractStringLine(line, "72-78", lineNumber));
    tx.setSaleDate(toStartOfDayUtc(FileParserUtils.extractDateLine(line, "21-29", lineNumber)));
    tx.setDiscountValue(BigDecimal.ZERO);
    tx.setLiquidValue(grossValue);
    tx.setMdrRate(BigDecimal.ZERO);
    tx.setFlexRate(BigDecimal.ZERO);
    tx.setTid(FileParserUtils.extractStringLine(line, "78-98", lineNumber));
    tx.setReferenceNumber(FileParserUtils.extractStringLine(line, "98-128", lineNumber));
    tx.setCapture(CaptureEnum.ECOMMERCE.getCode());
    tx.setInstallment("034".equals(recordType) ? 1 : inferInstallmentsFromSummary(summary));
    tx.setModality("034".equals(recordType) ? ModalityEnum.CASH_CREDIT.getCode() : resolveInstallmentModalityCode(tx.getInstallment()));
    tx.setFirstInstallmentValue(BigDecimal.ZERO);
    tx.setOtherInstallmentsValue(BigDecimal.ZERO);
    tx.setStatusAudit(STATUS_PENDING);
    tx.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    tx.setStatusTransaction(StatusTransactionEnum.PENDING);
    tx.setStatusTransactionReason(StatusTransactionReasonEnum.NULL);
    return tx;
  }

  private RequestNoticeEntity buildRequestNotice(String line, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries, boolean ecommerce) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    Integer rvNumber = FileParserUtils.extractIntegerLine(line, "12-21", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    RequestNoticeEntity notice = new RequestNoticeEntity();
    notice.setLineNumber(lineNumber);
    notice.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    notice.setPvNumber(pvNumber);
    notice.setRvNumber(rvNumber);
    notice.setRequestStatus(REQUEST_AWAITING_DECISION);
    notice.setAcquirer(acquirer);
    notice.setEstablishment(establishment);
    notice.setCompany(establishment != null ? establishment.getCompany() : null);
    notice.setSalesSummary(findSummary(summaries, pvNumber, rvNumber));
    notice.setProcessedFile(processedFile);

    if (ecommerce) {
      notice.setCardNumber(FileParserUtils.extractStringLine(line, "21-37", lineNumber));
      notice.setSaleDate(FileParserUtils.extractDateLine(line, "37-45", lineNumber));
      notice.setNsu(FileParserUtils.extractLongLine(line, "45-57", lineNumber));
      notice.setAuthorization(FileParserUtils.extractStringLine(line, "57-63", lineNumber));
      notice.setTid(FileParserUtils.extractStringLine(line, "63-83", lineNumber));
      notice.setEcommerceOrderNumber(FileParserUtils.extractStringLine(line, "83-113", lineNumber));
    } else {
      String acquirerCode = trim(FileParserUtils.extractStringLine(line, "120-121", lineNumber));
      notice.setCardNumber(FileParserUtils.extractStringLine(line, "21-37", lineNumber));
      notice.setTransactionValue(safeMoney(line, "37-52", lineNumber, "transaction_value", "rede_request_notice"));
      notice.setSaleDate(FileParserUtils.extractDateLine(line, "52-60", lineNumber));
      notice.setReferenceNumber(toBigInteger(FileParserUtils.extractStringLine(line, "60-75", lineNumber)));
      notice.setProcessNumber(toBigInteger(FileParserUtils.extractStringLine(line, "75-90", lineNumber)));
      notice.setNsu(FileParserUtils.extractLongLine(line, "90-102", lineNumber));
      notice.setAuthorization(FileParserUtils.extractStringLine(line, "102-108", lineNumber));
      notice.setRequestCode(FileParserUtils.extractIntegerLine(line, "108-112", lineNumber));
      notice.setDeadline(FileParserUtils.extractDateLine(line, "112-120", lineNumber));
      notice.setFlag(safeFlag(acquirer, acquirerCode));
    }
    return notice;
  }

  private AdjustmentEntity buildAdjustmentCvNsuCredit(String line, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    Integer rvNumber = FileParserUtils.extractIntegerLine(line, "12-21", lineNumber);

    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String acquirerCode = trim(FileParserUtils.extractStringLine(line, "118-119", lineNumber));

    AdjustmentEntity adjustment = new AdjustmentEntity();
    adjustment.setLineNumber(lineNumber);
    adjustment.setProcessedFile(processedFile);
    adjustment.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    adjustment.setSourceRecordIdentifier("011");
    adjustment.setAdjustmentType("EEVC_CREDIT_ADJUSTMENT");
    adjustment.setAdjustmentStatus(AdjustmentStatusEnum.PENDING);
    adjustment.setPvNumber(pvNumber);
    adjustment.setPvNumberAdjustment(pvNumber);
    adjustment.setRvNumberAdjustment(rvNumber);
    adjustment.setAdjustmentDate(FileParserUtils.extractDateLine(line, "21-29", lineNumber));
    adjustment.setAdjustmentValue(safeMoney(line, "29-44", lineNumber, "adjustment_value", "adjustment"));
    adjustment.setCreditDate(FileParserUtils.extractDateLine(line, "44-52", lineNumber));
    adjustment.setLiquidValue(safeMoney(line, "52-67", lineNumber, "credit_value", "adjustment"));
    adjustment.setNet(FileParserUtils.extractStringLine(line, "67-68", lineNumber));
    adjustment.setRawAdjustmentCode(FileParserUtils.extractStringLine(line, "88-90", lineNumber));
    adjustment.setAdjustmentReason(AdjustmentReasonEnum.fromCode(FileParserUtils.extractIntegerLine(line, "88-90", lineNumber)));
    adjustment.setAdjustmentDescription(FileParserUtils.extractStringLine(line, "90-118", lineNumber));
    adjustment.setAdjustmentReason2(FileParserUtils.extractIntegerLine(line, "119-123", lineNumber));
    SalesSummaryEntity summary = findSummary(summaries, pvNumber, rvNumber);

    adjustment.setAcquirer(acquirer);
    adjustment.setRvFlagOrigin(summary != null ? summary.getFlag() : null);
    adjustment.setRvFlagAdjustment(safeFlag(acquirer, acquirerCode));
    adjustment.setEstablishment(establishment);
    adjustment.setCompany(establishment != null ? establishment.getCompany() : null);
    adjustment.setSalesSummary(summary);
    return adjustment;
  }

  private EeVcRvInstallment buildRvInstallment(String line, int lineNumber) {
    return new EeVcRvInstallment(
      FileParserUtils.extractStringLine(line, "0-3", lineNumber),
      lineNumber,
      FileParserUtils.extractIntegerLine(line, "3-12", lineNumber),
      FileParserUtils.extractIntegerLine(line, "12-21", lineNumber),
      FileParserUtils.extractDateLine(line, "21-29", lineNumber),
      FileParserUtils.extractIntegerLine(line, "37-39", lineNumber),
      safeMoney(line, "39-54", lineNumber, "gross_value", "eevc_rv_installment"),
      safeMoney(line, "54-69", lineNumber, "discount_value", "eevc_rv_installment"),
      safeMoney(line, "69-84", lineNumber, "liquid_value", "eevc_rv_installment"),
      FileParserUtils.extractDateLine(line, "84-92", lineNumber)
    );
  }

  private RedeIcPlusTransactionEntity buildIcPlusTransaction(String line, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    Integer rvNumber = FileParserUtils.extractIntegerLine(line, "12-21", lineNumber);

    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    RedeIcPlusTransactionEntity ic = new RedeIcPlusTransactionEntity();
    ic.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    ic.setLineNumber(lineNumber);
    ic.setPvNumber(pvNumber);
    ic.setRvNumberOriginal(rvNumber);
    ic.setRvDateOriginal(FileParserUtils.extractDateLine(line, "21-29", lineNumber));
    ic.setNsu(FileParserUtils.extractLongLine(line, "29-41", lineNumber));
    ic.setTransactionDate(FileParserUtils.extractDateLine(line, "41-49", lineNumber));
    ic.setMcc(FileParserUtils.extractIntegerLine(line, "49-53", lineNumber));
    ic.setCardProfile(FileParserUtils.extractStringLine(line, "53-56", lineNumber));
    ic.setInterchangeValue(safeMoney(line, "56-71", lineNumber, "interchange_value", "ic_plus"));
    ic.setPlusValue(safeMoney(line, "71-86", lineNumber, "plus_value", "ic_plus"));
    ic.setEntryMode(FileParserUtils.extractStringLine(line, "86-89", lineNumber));
    ic.setAcquirer(acquirer);
    ic.setEstablishment(establishment);
    ic.setCompany(establishment != null ? establishment.getCompany() : null);
    ic.setSalesSummary(findSummary(summaries, pvNumber, rvNumber));
    ic.setProcessedFile(processedFile);
    return ic;
  }

  private SerasaConsultationEntity buildAuxiliaryConsultation(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);

    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    SerasaConsultationEntity consultation = new SerasaConsultationEntity();
    consultation.setLineNumber(lineNumber);
    consultation.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    consultation.setPvNumber(pvNumber);
    consultation.setNumberConsultationCarriedOut(FileParserUtils.extractIntegerLine(line, "12-17", lineNumber));
    consultation.setStartConsultationPeriod(FileParserUtils.extractDateLine(line, "17-25", lineNumber));
    consultation.setFlag(safeFlag(acquirer, FileParserUtils.extractStringLine(line, "25-26", lineNumber)));
    consultation.setAcquirer(acquirer);
    consultation.setEstablishment(establishment);
    consultation.setCompany(establishment != null ? establishment.getCompany() : null);
    consultation.setProcessedFile(processedFile);
    return consultation;
  }

  private SerasaConsultationEntity buildRechargeAsAuxiliaryConsultation(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);

    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    SerasaConsultationEntity recharge = new SerasaConsultationEntity();
    recharge.setLineNumber(lineNumber);
    recharge.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    recharge.setPvNumber(pvNumber);
    recharge.setNumberConsultationCarriedOut(FileParserUtils.extractIntegerLine(line, "12-21", lineNumber));
    recharge.setStartConsultationPeriod(FileParserUtils.extractDateLine(line, "21-29", lineNumber));
    recharge.setValueConsultationPeriod(safeMoney(line, "41-56", lineNumber, "recharge_value", "eevc_recharge"));
    recharge.setFlag(safeFlag(acquirer, FileParserUtils.extractStringLine(line, "77-78", lineNumber)));
    recharge.setAcquirer(acquirer);
    recharge.setEstablishment(establishment);
    recharge.setCompany(establishment != null ? establishment.getCompany() : null);
    recharge.setProcessedFile(processedFile);
    return recharge;
  }

  private TotalizerMatrixEntity buildTotalizerMatrix(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    TotalizerMatrixEntity matrix = new TotalizerMatrixEntity();
    matrix.setLineNumber(lineNumber);
    matrix.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    matrix.setPvNumber(pvNumber);
    matrix.setTotalGrossValue(safeMoney(line, "12-27", lineNumber, "total_gross_value", "totalizer_matrix"));
    matrix.setRejectedCvNsuQuantity(FileParserUtils.extractIntegerLine(line, "27-33", lineNumber));
    matrix.setTotalRejectedValue(safeMoney(line, "33-48", lineNumber, "total_rejected_value", "totalizer_matrix"));
    matrix.setTotalRotatingValue(safeMoney(line, "48-63", lineNumber, "total_rotating_value", "totalizer_matrix"));
    matrix.setTotalInstallmentValue(safeMoney(line, "63-78", lineNumber, "total_installment_value", "totalizer_matrix"));
    matrix.setTotalIataValue(safeMoney(line, "78-93", lineNumber, "total_iata_value", "totalizer_matrix"));
    matrix.setTotalDollarValue(safeMoney(line, "93-108", lineNumber, "total_dollar_value", "totalizer_matrix"));
    matrix.setTotalDiscountValue(safeMoney(line, "108-123", lineNumber, "total_discount_value", "totalizer_matrix"));
    matrix.setTotalLiquidValue(safeMoney(line, "123-138", lineNumber, "total_liquid_value", "totalizer_matrix"));
    matrix.setTotalTipValue(safeMoney(line, "138-153", lineNumber, "total_tip_value", "totalizer_matrix"));
    matrix.setTotalBoardingFeeValue(safeMoney(line, "153-168", lineNumber, "total_boarding_fee_value", "totalizer_matrix"));
    matrix.setAcceptedCvNsuQuantity(FileParserUtils.extractIntegerLine(line, "168-174", lineNumber));
    matrix.setProcessedFile(processedFile);
    matrix.setAcquirer(acquirer);
    matrix.setEstablishment(establishment);
    matrix.setCompany(establishment != null ? establishment.getCompany() : null);
    return matrix;
  }

  private ArchiveTrailerEntity buildArchiveTrailer(String line, int lineNumber, ProcessedFileEntity processedFile) {
    AcquirerEntity acquirer = safeAcquirer();

    ArchiveTrailerEntity trailer = new ArchiveTrailerEntity();
    trailer.setLineNumber(lineNumber);
    trailer.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    trailer.setNumberMatrices(FileParserUtils.extractIntegerLine(line, "3-7", lineNumber));
    trailer.setNumberRecords(FileParserUtils.extractIntegerLine(line, "7-13", lineNumber));
    trailer.setPvRequesting(FileParserUtils.extractIntegerLine(line, "13-22", lineNumber));
    trailer.setTotalGrossValue(safeMoney(line, "22-37", lineNumber, "total_gross_value", "archive_trailer"));
    trailer.setRejectedCvNsuQuantity(FileParserUtils.extractIntegerLine(line, "37-43", lineNumber));
    trailer.setTotalRejectedValue(safeMoney(line, "43-58", lineNumber, "total_rejected_value", "archive_trailer"));
    trailer.setTotalRotatingValue(safeMoney(line, "58-73", lineNumber, "total_rotating_value", "archive_trailer"));
    trailer.setTotalInstallmentValue(safeMoney(line, "73-88", lineNumber, "total_installment_value", "archive_trailer"));
    trailer.setTotalIataValue(safeMoney(line, "88-103", lineNumber, "total_iata_value", "archive_trailer"));
    trailer.setTotalDollarValue(safeMoney(line, "103-118", lineNumber, "total_dollar_value", "archive_trailer"));
    trailer.setTotalDiscountValue(safeMoney(line, "118-133", lineNumber, "total_discount_value", "archive_trailer"));
    trailer.setTotalLiquidValue(safeMoney(line, "133-148", lineNumber, "total_liquid_value", "archive_trailer"));
    trailer.setTotalTipValue(safeMoney(line, "148-163", lineNumber, "total_tip_value", "archive_trailer"));
    trailer.setTotalBoardingFeeValue(safeMoney(line, "163-178", lineNumber, "total_boarding_fee_value", "archive_trailer"));
    trailer.setAcceptedCvNsuQuantity(FileParserUtils.extractIntegerLine(line, "178-184", lineNumber));
    trailer.setProcessedFile(processedFile);
    trailer.setAcquirer(acquirer);
    return trailer;
  }

  private void addIfValid(List<TransactionAcqEntity> transactions, TransactionAcqEntity tx) {
    if (tx == null) {
      return;
    }

    mergeOrAddRedeTransaction(transactions, tx);
  }

  /**
   * Adiciona a transação à coleção ou mescla com um registro equivalente já lido.
   *
   * <p>Este método atua somente nas coleções temporárias da importação. Os registros
   * 034/035/036 continuam armazenados apenas em {@code ecommerceComplements} e nunca
   * são persistidos diretamente como uma nova transação.</p>
   */
  /**
   * Remove do lote as transações que já existem no banco (mesma identidade natural),
   * evitando duplicação quando o mesmo conjunto de vendas é reprocessado em um arquivo
   * com bytes diferentes (que escapa da guarda por hash de conteúdo).
   *
   * Estratégia: PULAR a transação já existente (não reinsere e não sobrescreve), para
   * preservar o estado de conciliação/ajustes já aplicado à transação no banco.
   *
   * @return quantidade de transações removidas do lote por já existirem.
   */
  private int removeAlreadyPersisted(List<TransactionAcqEntity> transactions) {
    if (transactions.isEmpty()) {
      return 0;
    }

    Set<Long> nsus = transactions.stream()
      .map(TransactionAcqEntity::getNsu)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    if (nsus.isEmpty()) {
      return 0;
    }

    List<TransactionAcqEntity> existing = transactionAcqRepository.findExistingByNsus(nsus);
    if (existing.isEmpty()) {
      return 0;
    }

    int removed = 0;
    Iterator<TransactionAcqEntity> it = transactions.iterator();
    while (it.hasNext()) {
      TransactionAcqEntity candidate = it.next();
      boolean duplicate = existing.stream()
        .anyMatch(persisted -> Objects.equals(persisted.getRecordType(), candidate.getRecordType())
          && isSameRedeTransaction(persisted, candidate));
      if (duplicate) {
        it.remove();
        removed++;
      }
    }

    return removed;
  }

  private void mergeOrAddRedeTransaction(List<TransactionAcqEntity> transactions, TransactionAcqEntity transaction) {
    Optional<TransactionAcqEntity> existing = transactions.stream()
      .filter(candidate -> Objects.equals(candidate.getRecordType(), transaction.getRecordType()))
      .filter(candidate -> isSameRedeTransaction(candidate, transaction))
      .findFirst();

    if (existing.isPresent()) {
      mergeRedeTransaction(existing.get(), transaction);
      return;
    }

    transactions.add(transaction);
  }

  private BigDecimal safeMoney(String line, String range, int lineNumber, String field, String context) {
    BigDecimal value = FileParserUtils.extractBigDecimalLine(line, range, lineNumber);
    if (value == null) return BigDecimal.ZERO;

    if (value.abs().compareTo(BigDecimal.valueOf(9_999_999_999L)) > 0) {
      RedeProcessingWarningCollector collector = warningCollector.get();
      if (collector != null) {
        collector.monetaryOutOfLimit(lineNumber, field, context, range, value);
      } else {
        log.debug("EEVC valor monetário fora do limite ignorado. linha={}, campo={}, contexto={}, range={}, valor={}",
          lineNumber, field, context, range, value);
      }
      return BigDecimal.ZERO;
    }
    return value;
  }

  private List<InstallmentAcqEntity> buildInstallments(List<TransactionAcqEntity> transactions, List<EeVcRvInstallment> layoutInstallments) {
    List<InstallmentAcqEntity> result = new ArrayList<>();
    Map<String, List<EeVcRvInstallment>> bySummary = new HashMap<>();

    for (EeVcRvInstallment installment : layoutInstallments) {
      bySummary
        .computeIfAbsent(summaryKey(installment.pvNumber(), installment.rvNumber()), ignored -> new ArrayList<>())
        .add(installment);
    }

    for (List<EeVcRvInstallment> items : bySummary.values()) {
      items.sort(Comparator.comparing(EeVcRvInstallment::installment, Comparator.nullsLast(Integer::compareTo)));
    }

    for (TransactionAcqEntity tx : transactions) {
      List<EeVcRvInstallment> layoutItems = null;

      if (tx.getSalesSummary() != null) {
        layoutItems = bySummary.get(summaryKey(tx.getSalesSummary().getPvNumber(), tx.getSalesSummary().getRvNumber()));
      }

      if (layoutItems != null && !layoutItems.isEmpty()) {
        BigDecimal summaryGross = tx.getSalesSummary() == null ? tx.getGrossValue() : tx.getSalesSummary().getGrossValue();
        BigDecimal transactionDiscount = BigDecimal.ZERO;
        BigDecimal transactionLiquid = BigDecimal.ZERO;

        for (EeVcRvInstallment item : layoutItems) {
          InstallmentAcqEntity installment = new InstallmentAcqEntity();
          installment.setTransaction(tx);
          installment.setInstallment(normalizeInstallmentCount(item.installment()));
          installment.setGrossValue(proportional(item.grossValue(), tx.getGrossValue(), summaryGross));
          installment.setDiscountValue(proportional(item.discountValue(), tx.getGrossValue(), summaryGross));
          installment.setLiquidValue(proportional(item.liquidValue(), tx.getGrossValue(), summaryGross));
          installment.setAdjustmentValue(BigDecimal.ZERO);
          installment.setStatusPaymentBank(STATUS_PENDING);
          installment.setInstallmentStatus(STATUS_PENDING);
          installment.setExpectedPaymentDate(item.creditDate());
          transactionDiscount = transactionDiscount.add(zero(installment.getDiscountValue()));
          transactionLiquid = transactionLiquid.add(zero(installment.getLiquidValue()));
          result.add(installment);
        }

        // Alguns registros e-commerce (034/035/036) não trazem desconto/MDR no próprio registro
        // da transação, mas as parcelas 014/020 trazem os valores corretos. Persistimos também
        // na transação para que a conciliação de taxa use cs_transaction_acq.mdr_rate corretamente.
        if (positive(transactionDiscount)) {
          tx.setDiscountValue(transactionDiscount);
          tx.setLiquidValue(transactionLiquid);
          tx.setMdrRate(calculateRate(tx.getGrossValue(), transactionDiscount));
        }
        continue;
      }

      int total = normalizeInstallmentCount(tx.getInstallment());
      BigDecimal grossPerInstallment = divide(tx.getGrossValue(), total);
      BigDecimal discountPerInstallment = divide(tx.getDiscountValue(), total);
      BigDecimal liquidPerInstallment = resolveLiquidInstallmentValue(tx, total);
      LocalDate firstDueDate = tx.getSalesSummary() != null ? tx.getSalesSummary().getFirstInstallmentCreditDate() : null;

      for (int i = 1; i <= total; i++) {
        InstallmentAcqEntity installment = new InstallmentAcqEntity();
        installment.setTransaction(tx);
        installment.setInstallment(i);
        installment.setGrossValue(grossPerInstallment);
        installment.setDiscountValue(discountPerInstallment);
        installment.setLiquidValue(i == 1 && positive(tx.getFirstInstallmentValue()) ? tx.getFirstInstallmentValue() : liquidPerInstallment);
        installment.setAdjustmentValue(BigDecimal.ZERO);
        installment.setStatusPaymentBank(STATUS_PENDING);
        installment.setInstallmentStatus(STATUS_PENDING);
        installment.setExpectedPaymentDate(firstDueDate == null ? null : firstDueDate.plusMonths(i - 1L));
        result.add(installment);
      }
    }
    return result;
  }

  private String summaryKey(Integer pvNumber, Integer rvNumber) {
    return String.valueOf(pvNumber) + "|" + String.valueOf(rvNumber);
  }

  private BigDecimal proportional(BigDecimal value, BigDecimal transactionGross, BigDecimal summaryGross) {
    if (value == null) return BigDecimal.ZERO;
    if (summaryGross == null || summaryGross.signum() == 0 || transactionGross == null || transactionGross.signum() == 0) {
      return value;
    }
    return value.multiply(transactionGross).divide(summaryGross, 2, RoundingMode.HALF_UP);
  }

  private BigDecimal resolveLiquidInstallmentValue(TransactionAcqEntity tx, int total) {
    if (total <= 1) return zero(tx.getLiquidValue());
    if (positive(tx.getOtherInstallmentsValue())) return tx.getOtherInstallmentsValue();
    return divide(tx.getLiquidValue(), total);
  }

  private BigDecimal divide(BigDecimal value, int divisor) {
    if (value == null || divisor <= 0) return BigDecimal.ZERO;
    return value.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
  }

  private boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }

  private BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private SalesSummaryEntity lastSummary(List<SalesSummaryEntity> summaries) {
    return summaries.isEmpty() ? null : summaries.get(summaries.size() - 1);
  }

  private SalesSummaryEntity findSummary(List<SalesSummaryEntity> summaries, Integer pvNumber, Integer rvNumber) {
    if (pvNumber == null || rvNumber == null) return lastSummary(summaries);
    for (int i = summaries.size() - 1; i >= 0; i--) {
      SalesSummaryEntity summary = summaries.get(i);
      if (Objects.equals(summary.getPvNumber(), pvNumber) && Objects.equals(summary.getRvNumber(), rvNumber)) {
        return summary;
      }
    }
    return lastSummary(summaries);
  }

  private AcquirerEntity safeAcquirer() {
    try {
      return lookupService.acquirerByIdentifier("REDE");
    } catch (Exception ex) {
      log.debug("Adquirente Rede não encontrado durante parsing EEVC: {}", ex.getMessage());
      return null;
    }
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    if (pvNumber == null) return null;
    try {
      return lookupService.establishmentByPvNumber(pvNumber);
    } catch (Exception ex) {
      log.debug("Estabelecimento não encontrado para PV {} durante parsing EEVC: {}", pvNumber, ex.getMessage());
      return null;
    }
  }

  private void ensureSalesSummariesBankingDomicile(List<SalesSummaryEntity> summaries) {
    if (summaries == null || summaries.isEmpty()) {
      return;
    }

    int resolved = 0;
    int unresolved = 0;

    for (SalesSummaryEntity summary : summaries) {
      if (summary == null || summary.getBankingDomicile() != null) {
        continue;
      }

      BankingDomicileEntity bankingDomicile = safeBankingDomicile(summary);
      if (bankingDomicile != null) {
        applyBankingDomicile(summary, bankingDomicile);
        resolved++;
      } else if (summary.getAgency() != null || summary.getCurrentAccount() != null) {
        unresolved++;
      }
    }

    if (resolved > 0 || unresolved > 0) {
      log.info("ℹ EEVC: domicílios bancários em resumos de vendas: resolvidos={}, pendentes={}", resolved, unresolved);
    }
  }

  private void applyBankingDomicile(SalesSummaryEntity summary, BankingDomicileEntity bankingDomicile) {
    if (summary == null || bankingDomicile == null) {
      return;
    }

    summary.setBankingDomicile(bankingDomicile);
    summary.setAgency(bankingDomicile.getAgency());
    summary.setCurrentAccount(bankingDomicile.getCurrentAccount());
  }

  private BankingDomicileEntity safeBankingDomicile(SalesSummaryEntity summary) {
    if (summary == null) {
      return null;
    }

    CompanyEntity company = summary.getCompany();
    if (company == null && summary.getPvNumber() != null) {
      EstablishmentEntity establishment = safeEstablishment(summary.getPvNumber());
      company = establishment != null ? establishment.getCompany() : null;
    }

    return safeBankingDomicile(summary.getBank(), company, summary.getAgency(), summary.getCurrentAccount());
  }

  private BankingDomicileEntity safeBankingDomicile(String bankCode, CompanyEntity company, Integer agency, Integer currentAccount) {
    try {
      return bankingDomicileResolver.resolve(bankCode, agency, currentAccount, company).orElse(null);
    } catch (Exception ex) {
      log.debug("Domicílio bancário não encontrado para banco={} agência={} conta={} no EEVC: {}",
        bankCode, agency, currentAccount, ex.getMessage());
      return null;
    }
  }

  private BankingDomicileEntity safeBankingDomicile(String bankCode, EstablishmentEntity establishment, Integer agency, Integer currentAccount) {
    CompanyEntity company = establishment != null ? establishment.getCompany() : null;
    return safeBankingDomicile(bankCode, company, agency, currentAccount);
  }

  private FlagEntity safeFlag(AcquirerEntity acquirer, String code) {
    if (acquirer == null || code == null || code.isBlank()) return null;
    try {
      return lookupService.flagByAcquirerCode(acquirer, code);
    } catch (Exception ex) {
      log.debug("Bandeira não encontrada para código Rede '{}' durante parsing EEVC: {}", code, ex.getMessage());
      return null;
    }
  }

  private Integer parseCapture(String line, int lineNumber, String range) {
    Integer code = FileParserUtils.extractIntegerLine(line, range, lineNumber);
    if (code == null) return CaptureEnum.NULL.getCode();
    return code;
  }

  private Integer normalizeInstallmentCount(Integer installments) {
    if (installments == null || installments <= 0) return 1;
    return installments;
  }

  private Integer inferInstallmentsFromSummary(SalesSummaryEntity summary) {
    if (summary == null || summary.getModality() == null) return 1;
    if (Objects.equals(summary.getModality(), ModalityEnum.INSTALLMENT_CREDIT_2_6.getCode())) return 2;
    if (Objects.equals(summary.getModality(), ModalityEnum.INSTALLMENT_CREDIT_7_12.getCode())) return 7;
    if (Objects.equals(summary.getModality(), ModalityEnum.INSTALLMENT_CREDIT_13_21.getCode())) return 13;
    return 1;
  }

  private Integer resolveInstallmentModalityCode(Integer installments) {
    int total = normalizeInstallmentCount(installments);
    if (total <= 1) return ModalityEnum.CASH_CREDIT.getCode();
    if (total <= 6) return ModalityEnum.INSTALLMENT_CREDIT_2_6.getCode();
    if (total <= 12) return ModalityEnum.INSTALLMENT_CREDIT_7_12.getCode();
    if (total <= 21) return ModalityEnum.INSTALLMENT_CREDIT_13_21.getCode();
    return ModalityEnum.OUTROS.getCode();
  }

  private BigDecimal calculateRate(BigDecimal grossValue, BigDecimal discountValue) {
    if (grossValue == null || grossValue.signum() == 0 || discountValue == null) return BigDecimal.ZERO;
    return discountValue.multiply(BigDecimal.valueOf(100)).divide(grossValue, 6, RoundingMode.HALF_UP);
  }

  private OffsetDateTime toStartOfDayUtc(LocalDate date) {
    return date == null ? null : date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
  }

  private BigInteger toBigInteger(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return new BigInteger(value.trim());
    } catch (Exception ex) {
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

  private enum TransactionLayout {
    ROTATIVO("ROTATIVO", ModalityEnum.CASH_CREDIT.getCode()),
    PARCELADO("PARCELADO", null),  // determinado pelo installment real da transação
    IATA("IATA", null),            // determinado pelo installment real da transação
    DOLLAR("DOLLAR", ModalityEnum.CASH_CREDIT.getCode());

    private final String summaryType;
    private final Integer summaryModality;

    TransactionLayout(String summaryType, Integer summaryModality) {
      this.summaryType = summaryType;
      this.summaryModality = summaryModality;
    }
  }

  /**
   * Associa os registros complementares de e-commerce às transações principais.
   *
   * <p>Os registros 034, 035 e 036 nunca representam uma nova venda e, por isso,
   * jamais são adicionados à lista de transações. Eles somente enriquecem a venda
   * principal equivalente já encontrada no mesmo arquivo:</p>
   *
   * <ul>
   *   <li>034 complementa 008;</li>
   *   <li>035 complementa 012;</li>
   *   <li>036 complementa 018.</li>
   * </ul>
   *
   * @return quantidade de complementos descartados por falta de transação principal.
   */
  private int mergeEcommerceComplements(List<TransactionAcqEntity> transactions, List<TransactionAcqEntity> ecommerceComplements) {
    int ignored = 0;

    for (TransactionAcqEntity complement : ecommerceComplements) {
      String expectedMainRecordType = expectedMainRecordType(complement.getRecordType());

      Optional<TransactionAcqEntity> mainTransaction = transactions.stream()
        .filter(tx -> Objects.equals(expectedMainRecordType, tx.getRecordType()))
        .filter(tx -> isSameRedeTransaction(tx, complement))
        .findFirst();

      if (mainTransaction.isEmpty()) {
        ignored++;
        log.debug(
          "Complemento Rede EEVC ignorado: recordType={}, linha={}, rv={}, nsu={}, autorização={}",
          complement.getRecordType(),
          complement.getLineNumber(),
          complement.getRvNumber(),
          complement.getNsu(),
          complement.getAuthorization()
        );
        continue;
      }

      mergeRedeTransaction(mainTransaction.get(), complement);
    }

    return ignored;
  }

  private String expectedMainRecordType(String ecommerceRecordType) {
    return switch (ecommerceRecordType) {
      case "034" -> "008";
      case "035" -> "012";
      case "036" -> "018";
      default -> null;
    };
  }

  private void mergeRedeTransaction(TransactionAcqEntity target, TransactionAcqEntity source) {
    if (target == null || source == null || target == source) {
      return;
    }

    boolean targetEcommerce = isEcommerceRecordType(target.getRecordType());
    boolean sourceEcommerce = isEcommerceRecordType(source.getRecordType());

    String currentTid = target.getTid();
    String ecommerceTid = !isBlank(source.getTid()) ? source.getTid() : currentTid;
    String currentReference = target.getReferenceNumber();

    /*
     * No EEVC, os registros 034/035/036 trazem dados próprios do e-commerce
     * (TID e pedido), mas normalmente não carregam desconto, líquido, MDR, status,
     * parcelas e demais dados completos da venda.
     *
     * Quando o registro e-commerce aparece antes do registro principal (008/012/018),
     * a versão antiga criava uma linha para o 034/035/036 e outra para o 008/012/018.
     * Aqui mantemos uma única entidade em memória e enriquecemos a mesma venda.
     */
    if (targetEcommerce && !sourceEcommerce) {
      copyMainTransactionData(target, source);
      if (!isBlank(ecommerceTid)) {
        target.setTid(ecommerceTid);
      }
      if (isBlank(target.getReferenceNumber()) && !isBlank(currentReference)) {
        target.setReferenceNumber(currentReference);
      }
      target.setCapture(CaptureEnum.ECOMMERCE.getCode());
      return;
    }

    mergeMissingTransactionData(target, source);

    if (!isBlank(source.getTid())) {
      target.setTid(source.getTid());
    }

    if (sourceEcommerce) {
      target.setCapture(CaptureEnum.ECOMMERCE.getCode());
    }

    if (isBlank(target.getReferenceNumber()) && !isBlank(source.getReferenceNumber())) {
      target.setReferenceNumber(source.getReferenceNumber());
    }
  }

  private void copyMainTransactionData(TransactionAcqEntity target, TransactionAcqEntity source) {
    target.setRecordType(source.getRecordType());
    target.setLineNumber(source.getLineNumber());
    target.setCompany(source.getCompany());
    target.setEstablishment(source.getEstablishment());
    target.setAcquirer(source.getAcquirer());
    target.setFlag(source.getFlag());
    target.setProcessedFile(source.getProcessedFile());
    target.setSalesSummary(source.getSalesSummary());
    target.setRvNumber(source.getRvNumber());
    target.setGrossValue(source.getGrossValue());
    target.setTipValue(source.getTipValue());
    target.setCardNumber(source.getCardNumber());
    target.setStatusCv(source.getStatusCv());
    target.setNsu(source.getNsu());
    target.setAuthorization(source.getAuthorization());
    target.setSaleDate(source.getSaleDate());
    target.setDiscountValue(source.getDiscountValue());
    target.setLiquidValue(source.getLiquidValue());
    target.setMachine(source.getMachine());
    target.setFlexRate(source.getFlexRate());
    target.setMdrRate(source.getMdrRate());
    target.setCapture(source.getCapture());
    target.setServiceCode(source.getServiceCode());
    target.setInstallment(source.getInstallment());
    target.setModality(source.getModality());
    target.setFirstInstallmentValue(source.getFirstInstallmentValue());
    target.setOtherInstallmentsValue(source.getOtherInstallmentsValue());
    target.setDccCurrency(source.getDccCurrency());
    target.setStatusAudit(source.getStatusAudit());
    target.setStatusPaymentBank(source.getStatusPaymentBank());
    target.setStatusTransaction(source.getStatusTransaction());
    target.setStatusTransactionReason(source.getStatusTransactionReason());
  }

  private void mergeMissingTransactionData(TransactionAcqEntity target, TransactionAcqEntity source) {
    if (target.getCompany() == null) target.setCompany(source.getCompany());
    if (target.getEstablishment() == null) target.setEstablishment(source.getEstablishment());
    if (target.getAcquirer() == null) target.setAcquirer(source.getAcquirer());
    if (target.getFlag() == null) target.setFlag(source.getFlag());
    if (target.getProcessedFile() == null) target.setProcessedFile(source.getProcessedFile());
    if (target.getSalesSummary() == null) target.setSalesSummary(source.getSalesSummary());
    if (target.getRvNumber() == null) target.setRvNumber(source.getRvNumber());
    if (target.getGrossValue() == null || target.getGrossValue().signum() == 0) target.setGrossValue(source.getGrossValue());
    if (target.getTipValue() == null || target.getTipValue().signum() == 0) target.setTipValue(source.getTipValue());
    if (isBlank(target.getCardNumber())) target.setCardNumber(source.getCardNumber());
    if (isBlank(target.getStatusCv())) target.setStatusCv(source.getStatusCv());
    if (target.getNsu() == null) target.setNsu(source.getNsu());
    if (isBlank(target.getAuthorization())) target.setAuthorization(source.getAuthorization());
    if (target.getSaleDate() == null) target.setSaleDate(source.getSaleDate());
    if (target.getDiscountValue() == null || target.getDiscountValue().signum() == 0) target.setDiscountValue(source.getDiscountValue());
    if (target.getLiquidValue() == null || target.getLiquidValue().signum() == 0) target.setLiquidValue(source.getLiquidValue());
    if (isBlank(target.getMachine())) target.setMachine(source.getMachine());
    if (target.getFlexRate() == null || target.getFlexRate().signum() == 0) target.setFlexRate(source.getFlexRate());
    if (target.getMdrRate() == null || target.getMdrRate().signum() == 0) target.setMdrRate(source.getMdrRate());
    if (target.getCapture() == null) target.setCapture(source.getCapture());
    if (isBlank(target.getServiceCode())) target.setServiceCode(source.getServiceCode());
    if (target.getInstallment() == null || target.getInstallment() <= 1) target.setInstallment(source.getInstallment());
    if (target.getModality() == null) target.setModality(source.getModality());
    if (target.getFirstInstallmentValue() == null || target.getFirstInstallmentValue().signum() == 0) target.setFirstInstallmentValue(source.getFirstInstallmentValue());
    if (target.getOtherInstallmentsValue() == null || target.getOtherInstallmentsValue().signum() == 0) target.setOtherInstallmentsValue(source.getOtherInstallmentsValue());
    if (isBlank(target.getDccCurrency())) target.setDccCurrency(source.getDccCurrency());
    if (target.getStatusAudit() == null) target.setStatusAudit(source.getStatusAudit());
    if (target.getStatusPaymentBank() == null) target.setStatusPaymentBank(source.getStatusPaymentBank());
    if (target.getStatusTransaction() == null) target.setStatusTransaction(source.getStatusTransaction());
    if (target.getStatusTransactionReason() == null) target.setStatusTransactionReason(source.getStatusTransactionReason());
  }

  private boolean isEcommerceRecordType(String recordType) {
    return "034".equals(recordType) || "035".equals(recordType) || "036".equals(recordType);
  }

  private boolean isSameRedeTransaction(TransactionAcqEntity a, TransactionAcqEntity b) {
    if (a == null || b == null) {
      return false;
    }

    if (a.getRvNumber() == null || b.getRvNumber() == null || a.getNsu() == null || b.getNsu() == null) {
      return false;
    }

    boolean ecommercePair = isEcommerceRecordType(a.getRecordType()) || isEcommerceRecordType(b.getRecordType());

    // O complemento de e-commerce pode trazer a data operacional anterior à data/hora
    // registrada na transação principal. Para esse par específico aceitamos diferença
    // máxima de um dia; para transações comuns continuamos exigindo o mesmo dia.
    boolean dateCriteria = compatibleSaleDate(a.getSaleDate(), b.getSaleDate(), ecommercePair);

    return Objects.equals(a.getRvNumber(), b.getRvNumber())
      && Objects.equals(a.getNsu(), b.getNsu())
      && compatibleAuthorization(a.getAuthorization(), b.getAuthorization())
      && sameMoney(a.getGrossValue(), b.getGrossValue())
      && sameCardNumber(a.getCardNumber(), b.getCardNumber())
      && sameEstablishment(a, b)
      && dateCriteria;
  }

  private boolean sameEstablishment(TransactionAcqEntity a, TransactionAcqEntity b) {
    if (a.getEstablishment() == null || b.getEstablishment() == null) {
      return true;
    }

    return Objects.equals(a.getEstablishment().getId(), b.getEstablishment().getId());
  }

  private boolean sameCardNumber(String a, String b) {
    String aa = normalizeNullableText(a);
    String bb = normalizeNullableText(b);

    if (aa == null || bb == null) {
      return true;
    }

    return aa.equals(bb);
  }

  private String normalizeNullableText(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private boolean compatibleAuthorization(String a, String b) {
    String aa = normalizeAuth(a);
    String bb = normalizeAuth(b);

    if (aa == null || bb == null) {
      return true;
    }

    return aa.equals(bb);
  }

  private String normalizeAuth(String value) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim();

    if (normalized.isBlank()) {
      return null;
    }

    return normalized.replaceFirst("^0+(?!$)", "");
  }

  private boolean compatibleSaleDate(OffsetDateTime a, OffsetDateTime b, boolean ecommercePair) {
    if (a == null || b == null) {
      return ecommercePair || Objects.equals(a, b);
    }

    long differenceDays = Math.abs(ChronoUnit.DAYS.between(a.toLocalDate(), b.toLocalDate()));
    return ecommercePair ? differenceDays <= 1 : differenceDays == 0;
  }

  private boolean sameMoney(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return Objects.equals(a, b);
    }

    return a.compareTo(b) == 0;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isBlank();
  }

  private record EeVcRvInstallment(
    String recordType,
    int lineNumber,
    Integer pvNumber,
    Integer rvNumber,
    LocalDate rvDate,
    Integer installment,
    BigDecimal grossValue,
    BigDecimal discountValue,
    BigDecimal liquidValue,
    LocalDate creditDate
  ) {
  }
}