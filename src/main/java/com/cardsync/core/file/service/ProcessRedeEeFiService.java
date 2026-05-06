package com.cardsync.core.file.service;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.FileParserUtils;
import com.cardsync.core.file.util.MoveFileService;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;
import com.cardsync.domain.model.enums.ProcessedFileErrorTypeEnum;
import com.cardsync.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRedeEeFiService {

  private static final int STATUS_PENDING = 1;
  private static final BigDecimal MAX_SAFE_MONEY = new BigDecimal("999999999.99");
  private static final Set<String> SUPPORTED_IDENTIFIERS = Set.of(
    "030", "032", "034", "035", "036", "037", "038", "040", "041", "042", "043", "044", "045", "046", "047", "048", "049", "050", "052", "053", "054", "055", "056", "057", "063", "064", "066", "069"
  );

  private final FileLookupService lookupService;
  private final MoveFileService moveFileService;
  private final ProcessedFileRepository processedFileRepository;
  private final CreditOrderRepository creditOrderRepository;
  private final AnticipationRepository anticipationRepository;
  private final CreditTotalizerRepository creditTotalizerRepository;
  private final SettledDebtRepository settledDebtRepository;
  private final BankingDomicileRepository bankingDomicileRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final PvMatrixHeaderRepository pvMatrixHeaderRepository;
  private final SerasaConsultationRepository serasaConsultationRepository;
  private final PendingDebtRepository pendingDebtRepository;
  private final InstallmentUnschedulingRepository installmentUnschedulingRepository;
  private final TotalizerMatrixRepository totalizerMatrixRepository;
  private final ArchiveTrailerRepository archiveTrailerRepository;
  private final AdjustmentRepository adjustmentRepository;
  private final RedeNegotiatedTransactionRepository negotiatedTransactionRepository;
  private final RedePixCancellationRepository pixCancellationRepository;
  private final RedeSuspendedPaymentRepository suspendedPaymentRepository;
  private final RedeTechnicalReserveRepository technicalReserveRepository;
  private final ThreadLocal<RedeProcessingWarningCollector> warningCollector = new ThreadLocal<>();

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths) {
    ProcessedFileEntity processedFile = null;
    try {
      warningCollector.set(new RedeProcessingWarningCollector("EEFI"));
      log.info("▶ Iniciando leitura Rede EEFI: {}", file.getFileName());
      var lines = Files.readAllLines(file);
      processedFile = createProcessedFile(file, lines.size());
      processedFile.markProcessing();

      List<PvMatrixHeaderEntity> pvMatrixHeaders = new ArrayList<>();
      List<CreditOrderEntity> creditOrders = new ArrayList<>();
      List<AnticipationEntity> anticipations = new ArrayList<>();
      List<CreditTotalizerEntity> creditTotalizers = new ArrayList<>();
      List<SerasaConsultationEntity> serasaConsultations = new ArrayList<>();
      List<PendingDebtEntity> pendingDebts = new ArrayList<>();
      List<SettledDebtEntity> settledDebts = new ArrayList<>();
      List<InstallmentUnschedulingEntity> unschedulings = new ArrayList<>();
      List<TotalizerMatrixEntity> totalizerMatrices = new ArrayList<>();
      List<ArchiveTrailerEntity> archiveTrailers = new ArrayList<>();
      List<AdjustmentEntity> financialAdjustments = new ArrayList<>();
      List<RedeNegotiatedTransactionEntity> negotiatedTransactions = new ArrayList<>();
      List<RedePixCancellationEntity> pixCancellations = new ArrayList<>();
      List<RedeSuspendedPaymentEntity> suspendedPayments = new ArrayList<>();
      List<RedeTechnicalReserveEntity> technicalReserves = new ArrayList<>();

      int recognized = 0;
      int ignored = 0;
      int warnings = 0;
      Set<String> unidentified = new HashSet<>();

      for (int i = 0; i < lines.size(); i++) {
        int lineNumber = i + 1;
        String line = lines.get(i);
        String identifier = FileParserUtils.extractStringLine(line, "0-3", lineNumber);
        if (identifier == null || identifier.isBlank()) {
          ignored++;
          warnings++;
          processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
            "REDE_EEFI_EMPTY_IDENTIFIER", "Linha EEFI sem identificador de registro.", line));
          continue;
        }
        if (!SUPPORTED_IDENTIFIERS.contains(identifier)) {
          ignored++;
          warnings++;
          unidentified.add(identifier);
          processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
            "REDE_EEFI_UNSUPPORTED_IDENTIFIER", "Identificador EEFI ainda não mapeado: " + identifier, line));
          continue;
        }

        recognized++;
        switch (identifier) {
          case "030" -> applyHeader(line, lineNumber, processedFile);
          case "032" -> pvMatrixHeaders.add(buildPvMatrixHeader(line, lineNumber, processedFile));
          case "034" -> creditOrders.add(buildCreditOrder(line, lineNumber, processedFile));
          case "035", "038", "043", "053" -> financialAdjustments.add(buildFinancialAdjustment(line, lineNumber, processedFile));
          case "041", "042" -> serasaConsultations.add(buildServiceConsultation(line, lineNumber, processedFile, identifier));
          case "046", "047", "048" -> negotiatedTransactions.add(buildNegotiatedTransaction(line, lineNumber, processedFile));
          case "054" -> financialAdjustments.add(buildEcommerceDebitAdjustment(line, lineNumber, processedFile));
          case "055" -> pendingDebts.add(buildPendingDebtEcommerce(line, lineNumber, processedFile));
          case "056" -> settledDebts.add(buildSettledDebtEcommerce(line, lineNumber, processedFile));
          case "063" -> pixCancellations.add(buildPixCancellation(line, lineNumber, processedFile));
          case "064" -> suspendedPayments.add(buildSuspendedPayment(line, lineNumber, processedFile));
          case "066" -> technicalReserves.add(buildTechnicalReserve(line, lineNumber, processedFile));
          case "069" -> unschedulings.add(buildInstallmentUnscheduling069(line, lineNumber, processedFile));
          case "036" -> anticipations.add(buildAnticipation(line, lineNumber, processedFile));
          case "037" -> creditTotalizers.add(buildCreditTotalizer(line, lineNumber, processedFile));
          case "040" -> serasaConsultations.add(buildSerasaConsultation(line, lineNumber, processedFile));
          case "044" -> pendingDebts.add(buildPendingDebt(line, lineNumber, processedFile));
          case "045" -> settledDebts.add(buildSettledDebt(line, lineNumber, processedFile));
          case "049" -> unschedulings.add(buildInstallmentUnscheduling(line, lineNumber, processedFile, false));
          case "050" -> totalizerMatrices.add(buildTotalizerMatrix(line, lineNumber, processedFile));
          case "052" -> archiveTrailers.add(buildArchiveTrailer(line, lineNumber, processedFile));
          case "057" -> unschedulings.add(buildInstallmentUnscheduling(line, lineNumber, processedFile, true));
          default -> {
            warnings++;
            processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
              "REDE_EEFI_RECORD_RECOGNIZED_NOT_PERSISTED",
              "Registro EEFI " + identifier + " reconhecido, mas sua entidade específica ainda não foi habilitada nesta etapa.",
              null));
          }
        }
      }

      RedeProcessingWarningCollector collector = warningCollector.get();
      if (collector != null && collector.hasWarnings()) {
        warnings += collector.totalWarnings();
        collector.addProcessedFileErrors(processedFile, "REDE_EEFI_MONETARY_OUT_OF_RANGE");
      }

      processedFile.setProcessedLines(recognized);
      processedFile.setIgnoredLines(ignored);
      processedFile.setWarningLines(warnings);
      processedFile.setErrorLines(0);
      processedFile.markFinished(warnings > 0 ? FileStatusEnum.PROCESSED_WITH_WARNINGS : FileStatusEnum.PROCESSED,
        "linhas=" + processedFile.getTotalLines()
          + ", reconhecidas=" + recognized
          + ", matrizes=" + pvMatrixHeaders.size()
          + ", ordensCredito=" + creditOrders.size()
          + ", antecipacoes=" + anticipations.size()
          + ", totalizadoresCredito=" + creditTotalizers.size()
          + ", serasa=" + serasaConsultations.size()
          + ", debitosPendentes=" + pendingDebts.size()
          + ", debitosLiquidados=" + settledDebts.size()
          + ", ajustesFinanceiros=" + financialAdjustments.size()
          + ", negociacoes=" + negotiatedTransactions.size()
          + ", pixCancelamentos=" + pixCancellations.size()
          + ", pagamentosSuspensos=" + suspendedPayments.size()
          + ", reservasTecnicas=" + technicalReserves.size()
          + ", desagendamentos=" + unschedulings.size()
          + ", totalizadoresMatriz=" + totalizerMatrices.size()
          + ", trailers=" + archiveTrailers.size()
          + ", ignoradas=" + ignored
          + ", avisos=" + warnings
          + ", avisosLayout=" + (collector == null ? 0 : collector.totalWarnings()));

      processedFileRepository.save(processedFile);
      pvMatrixHeaderRepository.saveAll(pvMatrixHeaders);
      creditOrderRepository.saveAll(creditOrders);
      anticipationRepository.saveAll(anticipations);
      creditTotalizerRepository.saveAll(creditTotalizers);
      serasaConsultationRepository.saveAll(serasaConsultations);
      pendingDebtRepository.saveAll(pendingDebts);
      settledDebtRepository.saveAll(settledDebts);
      installmentUnschedulingRepository.saveAll(unschedulings);
      totalizerMatrixRepository.saveAll(totalizerMatrices);
      archiveTrailerRepository.saveAll(archiveTrailers);
      adjustmentRepository.saveAll(financialAdjustments);
      negotiatedTransactionRepository.saveAll(negotiatedTransactions);
      pixCancellationRepository.saveAll(pixCancellations);
      suspendedPaymentRepository.saveAll(suspendedPayments);
      technicalReserveRepository.saveAll(technicalReserves);
      moveFileService.moveAfterCommit(file, paths.getProcessed());

      if (!unidentified.isEmpty()) {
        log.warn("⚠ EEFI {} possui identificadores não mapeados: {}", file.getFileName(), unidentified);
      }
      if (collector != null) {
        collector.logSummary(log, file.getFileName().toString());
      }
      log.info("✅ EEFI {} finalizado: status={}, {}", file.getFileName(), processedFile.getStatus(), processedFile.getStatusMessage());
    } catch (DataIntegrityViolationException ex) {
      log.error("⚠ Arquivo EEFI {} já processado anteriormente.", file.getFileName());
      if (processedFile != null) processedFile.setStatus(FileStatusEnum.DUPLICATE);
      moveFileService.moveAfterRollback(file, paths.getDuplicate());
      throw ex;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar EEFI {}: {}", file.getFileName(), ex.getMessage(), ex);
      if (processedFile != null) {
        processedFile.setStatus(FileStatusEnum.ERROR);
        processedFile.setErrorMessage(safeMessage(ex));
      }
      moveFileService.moveAfterRollback(file, paths.getError());
      throw new IllegalStateException(ex);
    } finally {
      warningCollector.remove();
    }
  }

  private ProcessedFileEntity createProcessedFile(Path file, int totalLines) {
    ProcessedFileEntity processedFile = new ProcessedFileEntity();
    processedFile.setOriginFile(lookupService.origin("REDE"));
    processedFile.setGroup(FileGroupEnum.ADQ);
    processedFile.setStatus(FileStatusEnum.RECEIVED);
    processedFile.setDateImport(OffsetDateTime.now());
    processedFile.setDateProcessing(OffsetDateTime.now());
    processedFile.setFile(file.getFileName().toString());
    processedFile.setTypeFile("Rede EEFI - Extrato financeiro");
    processedFile.setVersion("EEFI");
    processedFile.setTotalLines(totalLines);
    return processedFile;
  }

  private void applyHeader(String line, int lineNumber, ProcessedFileEntity processedFile) {
    processedFile.setDateFile(FileParserUtils.extractDateLine(line, "3-11", lineNumber));
    processedFile.setTypeFile(FileParserUtils.extractStringLine(line, "19-53", lineNumber));
    processedFile.setCommercialName(FileParserUtils.extractStringLine(line, "53-75", lineNumber));
    processedFile.setPvGroupNumber(FileParserUtils.extractIntegerLine(line, "81-90", lineNumber));
    processedFile.setVersion(FileParserUtils.extractStringLine(line, "105-125", lineNumber));
  }

  private CreditOrderEntity buildCreditOrder(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    Integer agency = FileParserUtils.extractIntegerLine(line, "50-56", lineNumber);
    Integer currentAccount = FileParserUtils.extractIntegerLine(line, "56-67", lineNumber);
    String acquirerCode = trim(FileParserUtils.extractStringLine(line, "92-93", lineNumber));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    CreditOrderEntity order = new CreditOrderEntity();
    order.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    order.setLineNumber(lineNumber);
    order.setPvCentralizer(pvNumber);
    order.setCreditOrderNumber(FileParserUtils.extractLongLine(line, "12-23", lineNumber));
    order.setReleaseDate(FileParserUtils.extractDateLine(line, "23-31", lineNumber));
    order.setReleaseValue(optionalMoneyLine(line, "31-46", lineNumber, "adjustment_value"));
    order.setLaunchType(FileParserUtils.extractStringLine(line, "46-47", lineNumber));
    order.setCreditOrderDate(FileParserUtils.extractDateLine(line, "67-75", lineNumber));
    order.setRvNumber(FileParserUtils.extractIntegerLine(line, "75-84", lineNumber));
    order.setRvDate(FileParserUtils.extractDateLine(line, "84-92", lineNumber));
    order.setTransactionType(FileParserUtils.extractIntegerLine(line, "93-94", lineNumber));
    order.setGrossRvValue(FileParserUtils.extractBigDecimalLine(line, "94-109", lineNumber));
    order.setDiscountRateValue(FileParserUtils.extractBigDecimalLine(line, "109-124", lineNumber));
    order.setInstallmentNumber(FileParserUtils.extractIntegerLine(line, "124-126", lineNumber));
    order.setInstallmentTotal(FileParserUtils.extractIntegerLine(line, "127-129", lineNumber));
    order.setCreditStatus(FileParserUtils.extractIntegerLine(line, "129-131", lineNumber));
    order.setOriginalPvNumber(FileParserUtils.extractIntegerLine(line, "131-140", lineNumber));
    order.setStatusPaymentBank(STATUS_PENDING);
    order.setSalesSummaryStatus(STATUS_PENDING);
    order.setReconciliationStatus(STATUS_PENDING);
    order.setSalesSummary(safeSalesSummary(acquirer, pvNumber, order.getRvNumber()));
    order.setProcessedFile(processedFile);
    order.setAcquirer(acquirer);
    order.setFlag(safeFlag(acquirer, acquirerCode));
    order.setCompany(establishment != null ? establishment.getCompany() : null);
    order.setBankingDomicile(safeDomicile(agency, currentAccount, order.getCompany()));
    return order;
  }

  private AnticipationEntity buildAnticipation(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    Integer agency = FileParserUtils.extractIntegerLine(line, "50-56", lineNumber);
    Integer currentAccount = FileParserUtils.extractIntegerLine(line, "56-67", lineNumber);
    String acquirerCode = trim(FileParserUtils.extractStringLine(line, "151-152", lineNumber));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    AnticipationEntity anticipation = new AnticipationEntity();
    anticipation.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    anticipation.setLineNumber(lineNumber);
    anticipation.setPvNumber(pvNumber);
    anticipation.setAgency(agency);
    anticipation.setCurrentAccount(currentAccount);
    anticipation.setNumberDocument(FileParserUtils.extractIntegerLine(line, "12-23", lineNumber));
    anticipation.setReleaseDate(FileParserUtils.extractDateLine(line, "23-31", lineNumber));
    anticipation.setReleaseValue(optionalMoneyLine(line, "31-46", lineNumber, "adjustment_value"));
    anticipation.setCredit(FileParserUtils.extractStringLine(line, "46-47", lineNumber));
    anticipation.setBank(FileParserUtils.extractStringLine(line, "47-50", lineNumber));
    anticipation.setNumberRvCorresponding(FileParserUtils.extractIntegerLine(line, "67-76", lineNumber));
    anticipation.setDateRvCorresponding(FileParserUtils.extractDateLine(line, "76-84", lineNumber));
    anticipation.setOriginalCreditValue(FileParserUtils.extractBigDecimalLine(line, "84-99", lineNumber));
    anticipation.setOriginalDueDate(FileParserUtils.extractDateLine(line, "99-107", lineNumber));
    anticipation.setInstallmentNumber(FileParserUtils.extractIntegerLine(line, "107-109", lineNumber));
    anticipation.setInstallmentNumberMax(FileParserUtils.extractIntegerLine(line, "110-112", lineNumber));
    anticipation.setGrossValue(FileParserUtils.extractBigDecimalLine(line, "112-127", lineNumber));
    anticipation.setDiscountRateValue(FileParserUtils.extractBigDecimalLine(line, "127-142", lineNumber));
    anticipation.setPvNumberOriginal(FileParserUtils.extractIntegerLine(line, "142-151", lineNumber));
    anticipation.setProcessedFile(processedFile);
    anticipation.setAcquirer(acquirer);
    anticipation.setFlag(safeFlag(acquirer, acquirerCode));
    anticipation.setEstablishment(establishment);
    anticipation.setCompany(establishment != null ? establishment.getCompany() : null);
    anticipation.setBankingDomicile(safeDomicile(agency, currentAccount, anticipation.getCompany()));
    anticipation.setSalesSummary(safeSalesSummary(acquirer, pvNumber, anticipation.getNumberRvCorresponding()));
    return anticipation;
  }

  private CreditTotalizerEntity buildCreditTotalizer(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    Integer agency = FileParserUtils.extractIntegerLine(line, "46-52", lineNumber);
    Integer currentAccount = FileParserUtils.extractIntegerLine(line, "52-63", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    CreditTotalizerEntity totalizer = new CreditTotalizerEntity();
    totalizer.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    totalizer.setLineNumber(lineNumber);
    totalizer.setPvNumber(pvNumber);
    totalizer.setCreditDate(FileParserUtils.extractDateLine(line, "19-27", lineNumber));
    totalizer.setTotalCreditValue(FileParserUtils.extractBigDecimalLine(line, "28-42", lineNumber));
    totalizer.setFileGenerationDate(FileParserUtils.extractDateLine(line, "63-71", lineNumber));
    totalizer.setAdvanceCreditDate(FileParserUtils.extractDateLine(line, "71-79", lineNumber));
    totalizer.setTotalValueAdvanceCredits(FileParserUtils.extractBigDecimalLine(line, "79-94", lineNumber));
    totalizer.setProcessedFile(processedFile);
    totalizer.setAcquirer(acquirer);
    totalizer.setCompany(establishment != null ? establishment.getCompany() : null);
    totalizer.setBankingDomicile(safeDomicile(agency, currentAccount, totalizer.getCompany()));
    return totalizer;
  }

  private SettledDebtEntity buildSettledDebt(String line, int lineNumber, ProcessedFileEntity processedFile) {
    String acquirerCode = trim(FileParserUtils.extractStringLine(line, "271-272", lineNumber));
    AcquirerEntity acquirer = safeAcquirer();

    SettledDebtEntity debt = new SettledDebtEntity();
    debt.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    debt.setLineNumber(lineNumber);
    debt.setPvNumber(FileParserUtils.extractIntegerLine(line, "3-12", lineNumber));
    debt.setNumberDebitOrder(FileParserUtils.extractLongLine(line, "12-23", lineNumber));
    debt.setDateDebitOrder(FileParserUtils.extractDateLine(line, "23-31", lineNumber));
    debt.setValueDebitOrder(optionalMoneyLine(line, "31-46", lineNumber, "adjustment_value"));
    debt.setReasonCode(FileParserUtils.extractIntegerLine(line, "46-48", lineNumber));
    debt.setReasonDescription(FileParserUtils.extractStringLine(line, "48-76", lineNumber));
    debt.setCardNumber(FileParserUtils.extractStringLine(line, "76-92", lineNumber));
    debt.setNsu(FileParserUtils.extractLongLine(line, "92-104", lineNumber));
    debt.setDateOriginalTransaction(FileParserUtils.extractDateLine(line, "104-112", lineNumber));
    debt.setAuthorization(FileParserUtils.extractStringLine(line, "112-118", lineNumber));
    debt.setOriginalTransactionValue(optionalMoneyLine(line, "118-133", lineNumber, "transaction_value"));
    debt.setNumberRvOriginal(FileParserUtils.extractIntegerLine(line, "133-142", lineNumber));
    debt.setDateRvOriginal(FileParserUtils.extractDateLine(line, "142-150", lineNumber));
    debt.setPvNumberOriginal(FileParserUtils.extractIntegerLine(line, "150-159", lineNumber));
    debt.setLetterNumber(FileParserUtils.extractLongLine(line, "159-174", lineNumber));
    debt.setLetterDate(FileParserUtils.extractDateLine(line, "174-182", lineNumber));
    debt.setNumberProcessChargeback(FileParserUtils.extractLongLine(line, "182-197", lineNumber));
    debt.setReferenceMonth(FileParserUtils.extractStringLine(line, "197-203", lineNumber));
    debt.setLiquidatedValue(optionalMoneyLine(line, "203-218", lineNumber, "total_debit_value"));
    debt.setLiquidatedDate(FileParserUtils.extractDateLine(line, "218-226", lineNumber));
    debt.setRetentionProcessNumber(FileParserUtils.extractLongLine(line, "226-241", lineNumber));
    debt.setCodeCompensation(FileParserUtils.extractIntegerLine(line, "241-243", lineNumber));
    debt.setCompensation(FileParserUtils.extractStringLine(line, "243-271", lineNumber));
    debt.setCodeReasonAdjustment2(FileParserUtils.extractIntegerLine(line, "272-276", lineNumber));
    debt.setRequestedCancellationValue(FileParserUtils.extractBigDecimalLine(line, "276-291", lineNumber));
    debt.setProcessedFile(processedFile);
    debt.setAcquirer(acquirer);
    debt.setFlag(safeFlag(acquirer, acquirerCode));
    return debt;
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

  private SerasaConsultationEntity buildSerasaConsultation(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    SerasaConsultationEntity serasa = new SerasaConsultationEntity();
    serasa.setLineNumber(lineNumber);
    serasa.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    serasa.setServiceType("040".equals(serasa.getRecordType()) ? "SERASA" : serasa.getRecordType());
    serasa.setPvNumber(pvNumber);
    serasa.setNumberConsultationCarriedOut(FileParserUtils.extractIntegerLine(line, "12-17", lineNumber));
    serasa.setTotalValueConsultation(FileParserUtils.extractBigDecimalLine(line, "17-32", lineNumber));
    serasa.setStartConsultationPeriod(FileParserUtils.extractDateLine(line, "32-40", lineNumber));
    serasa.setEndConsultationPeriod(FileParserUtils.extractDateLine(line, "40-48", lineNumber));
    serasa.setValueConsultationPeriod(FileParserUtils.extractBigDecimalLine(line, "48-63", lineNumber));
    serasa.setProcessedFile(processedFile);
    serasa.setAcquirer(acquirer);
    serasa.setEstablishment(establishment);
    serasa.setCompany(establishment != null ? establishment.getCompany() : null);
    return serasa;
  }

  private PendingDebtEntity buildPendingDebt(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    String acquirerCode = trim(FileParserUtils.extractStringLine(line, "286-287", lineNumber));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    PendingDebtEntity debt = new PendingDebtEntity();
    debt.setLineNumber(lineNumber);
    debt.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    debt.setPvNumber(pvNumber);
    debt.setNumberDebitOrder(FileParserUtils.extractLongLine(line, "12-23", lineNumber));
    debt.setDateDebitOrder(FileParserUtils.extractDateLine(line, "23-31", lineNumber));
    debt.setValueDebitOrder(optionalMoneyLine(line, "31-46", lineNumber, "adjustment_value"));
    debt.setReasonCode(FileParserUtils.extractIntegerLine(line, "46-48", lineNumber));
    debt.setReasonDescription(FileParserUtils.extractStringLine(line, "48-76", lineNumber));
    debt.setCardNumber(FileParserUtils.extractStringLine(line, "76-92", lineNumber));
    debt.setNsu(FileParserUtils.extractLongLine(line, "92-104", lineNumber));
    debt.setDateOriginalTransaction(FileParserUtils.extractDateLine(line, "104-112", lineNumber));
    debt.setAuthorization(FileParserUtils.extractStringLine(line, "112-118", lineNumber));
    debt.setOriginalTransactionValue(optionalMoneyLine(line, "118-133", lineNumber, "transaction_value"));
    debt.setNumberRvOriginal(FileParserUtils.extractIntegerLine(line, "133-142", lineNumber));
    debt.setDateRvOriginal(FileParserUtils.extractDateLine(line, "142-150", lineNumber));
    debt.setPvNumberOriginal(FileParserUtils.extractIntegerLine(line, "150-159", lineNumber));
    debt.setLetterNumber(FileParserUtils.extractLongLine(line, "159-174", lineNumber));
    debt.setLetterDate(FileParserUtils.extractDateLine(line, "174-182", lineNumber));
    debt.setNumberProcessChargeback(FileParserUtils.extractLongLine(line, "182-197", lineNumber));
    debt.setReferenceMonth(FileParserUtils.extractStringLine(line, "197-203", lineNumber));
    debt.setCompensatedValue(optionalMoneyLine(line, "203-218", lineNumber, "compensated_value"));
    debt.setPaymentDate(FileParserUtils.extractDateLine(line, "218-226", lineNumber));
    debt.setPendingValue(optionalMoneyLine(line, "226-241", lineNumber, "pending_value"));
    debt.setRetentionProcessNumber(FileParserUtils.extractLongLine(line, "241-256", lineNumber));
    debt.setCompensationCode(FileParserUtils.extractIntegerLine(line, "256-258", lineNumber));
    debt.setCompensationDescription(FileParserUtils.extractStringLine(line, "258-286", lineNumber));
    debt.setReasonCode2(FileParserUtils.extractIntegerLine(line, "287-291", lineNumber));
    debt.setProcessedFile(processedFile);
    debt.setAcquirer(acquirer);
    debt.setFlag(safeFlag(acquirer, acquirerCode));
    debt.setEstablishment(establishment);
    debt.setCompany(establishment != null ? establishment.getCompany() : null);
    return debt;
  }

  private InstallmentUnschedulingEntity buildInstallmentUnscheduling(String line, int lineNumber, ProcessedFileEntity processedFile, boolean ecommerce) {
    AcquirerEntity acquirer = safeAcquirer();
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String acquirerCode = ecommerce ? null : trim(FileParserUtils.extractStringLine(line, "166-167", lineNumber));

    InstallmentUnschedulingEntity unscheduling = new InstallmentUnschedulingEntity();
    unscheduling.setAcquirer(acquirer);
    unscheduling.setLineNumber(lineNumber);
    unscheduling.setEcommerce(ecommerce);
    unscheduling.setUnschedulingStatus(STATUS_PENDING);
    unscheduling.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    unscheduling.setPvNumberOriginal(pvNumber);
    unscheduling.setRvNumberOriginal(FileParserUtils.extractIntegerLine(line, "12-21", lineNumber));

    if (ecommerce) {
      unscheduling.setRvValueOriginal(FileParserUtils.extractBigDecimalLine(line, "21-36", lineNumber));
      unscheduling.setCardNumber(FileParserUtils.extractStringLine(line, "36-52", lineNumber));
      unscheduling.setTransactionDate(FileParserUtils.extractDateLine(line, "52-60", lineNumber));
      unscheduling.setNsu(FileParserUtils.extractLongLine(line, "60-72", lineNumber));
      unscheduling.setTid(FileParserUtils.extractStringLine(line, "72-92", lineNumber));
      unscheduling.setOrderNumber(FileParserUtils.extractStringLine(line, "92-122", lineNumber));
    } else {
      unscheduling.setReferenceNumber(FileParserUtils.extractStringLine(line, "21-36", lineNumber));
      unscheduling.setDateCredit(FileParserUtils.extractDateLine(line, "36-44", lineNumber));
      unscheduling.setNewInstallmentValue(optionalMoneyLine(line, "44-59", lineNumber, "adjustment_value"));
      unscheduling.setOriginalValueChangedInstallment(FileParserUtils.extractBigDecimalLine(line, "59-74", lineNumber));
      unscheduling.setAdjustmentValue(FileParserUtils.extractBigDecimalLine(line, "74-89", lineNumber));
      unscheduling.setCancellationDate(FileParserUtils.extractDateLine(line, "89-97", lineNumber));
      unscheduling.setRvValueOriginal(FileParserUtils.extractBigDecimalLine(line, "97-112", lineNumber));
      unscheduling.setCancellationValue(FileParserUtils.extractBigDecimalLine(line, "112-127", lineNumber));
      unscheduling.setCardNumber(FileParserUtils.extractStringLine(line, "127-143", lineNumber));
      unscheduling.setTransactionDate(FileParserUtils.extractDateLine(line, "143-151", lineNumber));
      unscheduling.setNsu(FileParserUtils.extractLongLine(line, "151-163", lineNumber));
      unscheduling.setTypeDebit(FileParserUtils.extractStringLine(line, "163-164", lineNumber));
      unscheduling.setNumberInstallment(FileParserUtils.extractIntegerLine(line, "164-166", lineNumber));
      unscheduling.setFlagRvOrigin(safeFlag(acquirer, acquirerCode));
    }

    unscheduling.setProcessedFile(processedFile);
    unscheduling.setEstablishment(establishment);
    unscheduling.setCompany(establishment != null ? establishment.getCompany() : null);
    return unscheduling;
  }

  private TotalizerMatrixEntity buildTotalizerMatrix(String line, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    TotalizerMatrixEntity matrix = new TotalizerMatrixEntity();
    matrix.setLineNumber(lineNumber);
    matrix.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    matrix.setPvNumber(pvNumber);
    matrix.setTotalNumberMatrixSummaries(FileParserUtils.extractIntegerLine(line, "12-18", lineNumber));
    matrix.setTotalValueNormalCredits(FileParserUtils.extractBigDecimalLine(line, "18-33", lineNumber));
    matrix.setValueAdvanceCredits(FileParserUtils.extractIntegerLine(line, "33-39", lineNumber));
    matrix.setTotalValueAnticipated(FileParserUtils.extractBigDecimalLine(line, "39-54", lineNumber));
    matrix.setAmountCreditAdjustments(FileParserUtils.extractIntegerLine(line, "54-58", lineNumber));
    matrix.setTotalValueCreditAdjustments(FileParserUtils.extractBigDecimalLine(line, "58-73", lineNumber));
    matrix.setAmountDebitAdjustments(FileParserUtils.extractIntegerLine(line, "73-79", lineNumber));
    matrix.setTotalValueDebitAdjustments(FileParserUtils.extractBigDecimalLine(line, "79-94", lineNumber));
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
    trailer.setNormalCreditsQuantity(FileParserUtils.extractIntegerLine(line, "22-26", lineNumber));
    trailer.setTotalValueRv(FileParserUtils.extractBigDecimalLine(line, "26-41", lineNumber));
    trailer.setAdvanceCreditAmount(FileParserUtils.extractIntegerLine(line, "41-47", lineNumber));
    trailer.setTotalValueUpfront(FileParserUtils.extractBigDecimalLine(line, "47-62", lineNumber));
    trailer.setAmountCreditAdjustments(FileParserUtils.extractIntegerLine(line, "62-66", lineNumber));
    trailer.setTotalValueCreditAdjustments(FileParserUtils.extractBigDecimalLine(line, "66-81", lineNumber));
    trailer.setDebitAdjustmentQuantity(FileParserUtils.extractIntegerLine(line, "81-85", lineNumber));
    trailer.setTotalValueDebit(FileParserUtils.extractBigDecimalLine(line, "85-100", lineNumber));
    trailer.setProcessedFile(processedFile);
    trailer.setAcquirer(acquirer);
    return trailer;
  }

  private AdjustmentEntity buildFinancialAdjustment(String line, int lineNumber, ProcessedFileEntity processedFile) {
    String identifier = FileParserUtils.extractStringLine(line, "0-3", lineNumber);
    boolean ecommerce = "053".equals(identifier) || "054".equals(identifier);
    AcquirerEntity acquirer = safeAcquirer();
    Integer pvNumber = "053".equals(identifier) ? FileParserUtils.extractIntegerLine(line, "36-45", lineNumber) : FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String acquirerCode = resolveAdjustmentAcquirerCode(line, lineNumber, identifier);

    AdjustmentEntity adjustment = new AdjustmentEntity();
    adjustment.setLineNumber(lineNumber);
    adjustment.setProcessedFile(processedFile);
    adjustment.setRecordType(identifier);
    adjustment.setSourceRecordIdentifier(identifier);
    adjustment.setEcommerce(ecommerce);
    adjustment.setPvNumber(pvNumber);
    adjustment.setPvNumberOriginal(pvNumber);
    adjustment.setAdjustmentStatus(STATUS_PENDING);
    adjustment.setAcquirer(acquirer);
    adjustment.setEstablishment(establishment);
    adjustment.setCompany(establishment != null ? establishment.getCompany() : null);
    adjustment.setRvFlagAdjustment(safeFlag(acquirer, acquirerCode));

    switch (identifier) {
      case "035" -> fillLiquidAdjustment(line, lineNumber, adjustment);
      case "038" -> fillDebitAdjustment(line, lineNumber, adjustment);
      case "043" -> fillCreditAdjustment(line, lineNumber, adjustment);
      case "053" -> fillEcommerceLiquidAdjustment(line, lineNumber, adjustment);
      case "055" -> fillGenericAdjustment(line, lineNumber, adjustment);
      default -> fillGenericAdjustment(line, lineNumber, adjustment);
    }

    return adjustment;
  }

  private void fillLiquidAdjustment(String line, int lineNumber, AdjustmentEntity adjustment) {
    // Registro 035 no EEFI: ajuste líquido.
    // Mantido alinhado ao parser legado do Nimbus, que usa campos depois da posição 154
    // para valor da transação, cancelamento, NSU, bandeiras e dados da parcela ajustada.
    BigDecimal transactionValue = optionalMoneyLine(line, "154-169", lineNumber, "transaction_value");
    BigDecimal cancellationValue = optionalMoneyLine(line, "223-238", lineNumber, "cancellation_value_requested");

    adjustment.setAdjustmentType(FileParserUtils.extractStringLine(line, "44-45", lineNumber));
    adjustment.setRvNumberAdjustment(FileParserUtils.extractIntegerLine(line, "12-21", lineNumber));
    adjustment.setPvNumberAdjustment(adjustment.getPvNumber());
    adjustment.setAdjustmentDate(FileParserUtils.extractDateLine(line, "21-29", lineNumber));
    adjustment.setAdjustmentValue(optionalMoneyLine(line, "29-44", lineNumber, "adjustment_value"));
    adjustment.setAdjustmentReason(FileParserUtils.extractIntegerLine(line, "45-47", lineNumber));
    adjustment.setAdjustmentDescription(FileParserUtils.extractStringLine(line, "47-75", lineNumber));
    adjustment.setCardNumber(FileParserUtils.extractStringLine(line, "75-91", lineNumber));
    adjustment.setTransactionDate(FileParserUtils.extractDateLine(line, "91-99", lineNumber));
    adjustment.setRvNumberOriginal(FileParserUtils.extractIntegerLine(line, "99-108", lineNumber));
    adjustment.setLetterNumber(FileParserUtils.extractLongLine(line, "108-123", lineNumber));
    adjustment.setLetterDate(FileParserUtils.extractDateLine(line, "123-131", lineNumber));
    adjustment.setReferenceMonth(FileParserUtils.extractStringLine(line, "131-137", lineNumber));
    adjustment.setPvNumberOriginal(FileParserUtils.extractIntegerLine(line, "137-146", lineNumber));
    adjustment.setRvDateOriginal(FileParserUtils.extractDateLine(line, "146-154", lineNumber));
    adjustment.setTransactionValue(transactionValue);
    adjustment.setNet(FileParserUtils.extractStringLine(line, "169-170", lineNumber));
    adjustment.setCreditDate(FileParserUtils.extractDateLine(line, "170-178", lineNumber));
    adjustment.setNewInstallmentValue(optionalMoneyLine(line, "178-193", lineNumber, "new_installment_value"));
    adjustment.setOriginalValueInstallment(optionalMoneyLine(line, "193-208", lineNumber, "original_value_installment"));
    adjustment.setOriginalGrossSalesSummaryValue(optionalMoneyLine(line, "208-223", lineNumber, "original_gross_sales_summary_value"));
    adjustment.setCancellationValueRequested(cancellationValue);
    adjustment.setNewTransactionValue(nullToZero(transactionValue).subtract(nullToZero(cancellationValue)));
    adjustment.setNsu(FileParserUtils.extractLongLine(line, "238-250", lineNumber));
    adjustment.setAuthorization(FileParserUtils.extractStringLine(line, "250-256", lineNumber));
    adjustment.setDebitType(FileParserUtils.extractStringLine(line, "256-257", lineNumber));
    adjustment.setNumberDebitOrder(FileParserUtils.extractLongLine(line, "257-268", lineNumber));
    adjustment.setTotalDebitValue(optionalMoneyLine(line, "268-283", lineNumber, "total_debit_value"));
    adjustment.setPendingValue(optionalMoneyLine(line, "283-298", lineNumber, "pending_value"));
    adjustment.setRvNumberInstallmentAdjusted(FileParserUtils.extractIntegerLine(line, "300-302", lineNumber));
    adjustment.setRvNumberInstallmentOriginal(FileParserUtils.extractIntegerLine(line, "302-304", lineNumber));
    adjustment.setRvDateAdjusted(FileParserUtils.extractDateLine(line, "304-312", lineNumber));
    adjustment.setAdjustmentReason2(FileParserUtils.extractIntegerLine(line, "312-316", lineNumber));
  }

  private void fillDebitAdjustment(String line, int lineNumber, AdjustmentEntity adjustment) {
    adjustment.setAdjustmentType("DEBIT_BANK");
    adjustment.setNumberDebitOrder(FileParserUtils.extractLongLine(line, "12-23", lineNumber));
    adjustment.setAdjustmentDate(FileParserUtils.extractDateLine(line, "23-31", lineNumber));
    adjustment.setAdjustmentValue(optionalMoneyLine(line, "31-46", lineNumber, "debit_value"));
    adjustment.setRvNumberOriginal(FileParserUtils.extractIntegerLine(line, "67-76", lineNumber));
    adjustment.setRvDateOriginal(FileParserUtils.extractDateLine(line, "76-84", lineNumber));
    adjustment.setGrossValue(optionalMoneyLine(line, "84-99", lineNumber, "original_credit_value"));
    adjustment.setAdjustmentReason(FileParserUtils.extractIntegerLine(line, "99-101", lineNumber));
    adjustment.setAdjustmentDescription(FileParserUtils.extractStringLine(line, "101-129", lineNumber));
    adjustment.setCardNumber(FileParserUtils.extractStringLine(line, "129-145", lineNumber));
    adjustment.setLetterReference(FileParserUtils.extractStringLine(line, "145-160", lineNumber));
    adjustment.setReferenceMonth(FileParserUtils.extractStringLine(line, "160-166", lineNumber));
    adjustment.setLetterDate(FileParserUtils.extractDateLine(line, "166-174", lineNumber));
    adjustment.setCancellationValueRequested(optionalMoneyLine(line, "174-189", lineNumber, "requested_cancellation_value"));
    adjustment.setRawAdjustmentCode(FileParserUtils.extractStringLine(line, "189-204", lineNumber));
    adjustment.setPvNumberOriginal(FileParserUtils.extractIntegerLine(line, "204-213", lineNumber));
    adjustment.setTransactionDate(FileParserUtils.extractDateLine(line, "213-221", lineNumber));
    adjustment.setNsu(FileParserUtils.extractLongLine(line, "221-233", lineNumber));
    adjustment.setRvNumberAdjustment(FileParserUtils.extractIntegerLine(line, "233-242", lineNumber));
    adjustment.setCreditDate(FileParserUtils.extractDateLine(line, "242-250", lineNumber));
    adjustment.setTransactionValue(optionalMoneyLine(line, "250-265", lineNumber, "original_transaction_value"));
    adjustment.setAuthorization(FileParserUtils.extractStringLine(line, "265-271", lineNumber));
    adjustment.setDebitType(FileParserUtils.extractStringLine(line, "271-272", lineNumber));
    adjustment.setTotalDebitValue(optionalMoneyLine(line, "272-287", lineNumber, "total_debit_value"));
    adjustment.setPendingValue(optionalMoneyLine(line, "287-302", lineNumber, "pending_value"));
    adjustment.setAdjustmentReason2(FileParserUtils.extractIntegerLine(line, "303-307", lineNumber));
  }

  private void fillCreditAdjustment(String line, int lineNumber, AdjustmentEntity adjustment) {
    adjustment.setAdjustmentType("CREDIT");
    adjustment.setPvNumberAdjustment(adjustment.getPvNumber());
    adjustment.setRvNumberAdjustment(FileParserUtils.extractIntegerLine(line, "12-21", lineNumber));
    adjustment.setNumberDebitOrder(FileParserUtils.extractLongLine(line, "21-32", lineNumber));
    adjustment.setAdjustmentDate(FileParserUtils.extractDateLine(line, "32-40", lineNumber));
    adjustment.setCreditDate(FileParserUtils.extractDateLine(line, "40-48", lineNumber));
    adjustment.setAdjustmentValue(optionalMoneyLine(line, "48-63", lineNumber, "credit_value"));
    adjustment.setAdjustmentReason(FileParserUtils.extractIntegerLine(line, "84-86", lineNumber));
    adjustment.setAdjustmentDescription(FileParserUtils.extractStringLine(line, "86-114", lineNumber));
    adjustment.setAdjustmentReason2(FileParserUtils.extractIntegerLine(line, "115-119", lineNumber));
  }

  private void fillEcommerceLiquidAdjustment(String line, int lineNumber, AdjustmentEntity adjustment) {
    adjustment.setAdjustmentType("LIQUID_ECOMMERCE");
    adjustment.setCardNumber(FileParserUtils.extractStringLine(line, "3-19", lineNumber));
    adjustment.setTransactionDate(FileParserUtils.extractDateLine(line, "19-27", lineNumber));
    adjustment.setRvNumberOriginal(FileParserUtils.extractIntegerLine(line, "27-36", lineNumber));
    adjustment.setPvNumberOriginal(FileParserUtils.extractIntegerLine(line, "36-45", lineNumber));
    adjustment.setPvNumber(adjustment.getPvNumberOriginal());
    adjustment.setTransactionValue(optionalMoneyLine(line, "45-60", lineNumber, "transaction_value"));
    adjustment.setNsu(FileParserUtils.extractLongLine(line, "60-72", lineNumber));
    adjustment.setAuthorization(FileParserUtils.extractStringLine(line, "72-78", lineNumber));
    adjustment.setTid(FileParserUtils.extractStringLine(line, "78-98", lineNumber));
    adjustment.setEcommerceOrderNumber(FileParserUtils.extractStringLine(line, "98-128", lineNumber));
  }

  private void fillGenericAdjustment(String line, int lineNumber, AdjustmentEntity adjustment) {
    adjustment.setAdjustmentType("GENERIC_" + adjustment.getRecordType());
    adjustment.setRawAdjustmentCode(FileParserUtils.extractStringLine(line, "12-23", lineNumber));
    adjustment.setAdjustmentDate(optionalDateLine(line, "23-31", lineNumber));
    adjustment.setAdjustmentValue(optionalMoneyLine(line, "31-46", lineNumber));
    adjustment.setAdjustmentReason(optionalIntegerLine(line, "46-48", lineNumber));
    adjustment.setAdjustmentDescription(FileParserUtils.extractStringLine(line, "48-76", lineNumber));
    adjustment.setCardNumber(FileParserUtils.extractStringLine(line, "76-92", lineNumber));
    adjustment.setNsu(optionalLongLine(line, "92-104", lineNumber));
    adjustment.setTransactionDate(optionalDateLine(line, "104-112", lineNumber));
    adjustment.setAuthorization(FileParserUtils.extractStringLine(line, "112-118", lineNumber));
    adjustment.setTransactionValue(optionalMoneyLine(line, "118-133", lineNumber));
  }

  private SerasaConsultationEntity buildServiceConsultation(String line, int lineNumber, ProcessedFileEntity processedFile, String identifier) {
    SerasaConsultationEntity service = buildSerasaConsultation(line, lineNumber, processedFile);
    service.setServiceType("041".equals(identifier) ? "AVS" : "SECURE_CODE");
    if ("042".equals(identifier)) {
      service.setFlag(safeFlag(safeAcquirer(), trim(FileParserUtils.extractStringLine(line, "63-64", lineNumber))));
    }
    return service;
  }

  private AdjustmentEntity buildEcommerceDebitAdjustment(String line, int lineNumber, ProcessedFileEntity processedFile) {
    AcquirerEntity acquirer = safeAcquirer();
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "28-37", lineNumber);
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    AdjustmentEntity adjustment = new AdjustmentEntity();
    adjustment.setLineNumber(lineNumber);
    adjustment.setProcessedFile(processedFile);
    adjustment.setRecordType("054");
    adjustment.setSourceRecordIdentifier("054");
    adjustment.setEcommerce(true);
    adjustment.setAdjustmentType("DEBIT_ECOMMERCE");
    adjustment.setPvNumber(pvNumber);
    adjustment.setPvNumberOriginal(pvNumber);
    adjustment.setAdjustmentStatus(STATUS_PENDING);
    adjustment.setAcquirer(acquirer);
    adjustment.setEstablishment(establishment);
    adjustment.setCompany(establishment != null ? establishment.getCompany() : null);
    adjustment.setRvNumberOriginal(FileParserUtils.extractIntegerLine(line, "3-12", lineNumber));
    adjustment.setCardNumber(FileParserUtils.extractStringLine(line, "12-28", lineNumber));
    adjustment.setTransactionDate(FileParserUtils.extractDateLine(line, "37-45", lineNumber));
    adjustment.setNsu(FileParserUtils.extractLongLine(line, "45-57", lineNumber));
    adjustment.setTransactionValue(optionalMoneyLine(line, "57-72", lineNumber, "transaction_value"));
    adjustment.setAuthorization(FileParserUtils.extractStringLine(line, "72-78", lineNumber));
    adjustment.setTid(FileParserUtils.extractStringLine(line, "78-98", lineNumber));
    adjustment.setEcommerceOrderNumber(FileParserUtils.extractStringLine(line, "98-128", lineNumber));
    return adjustment;
  }

  private PendingDebtEntity buildPendingDebtEcommerce(String line, int lineNumber, ProcessedFileEntity processedFile) {
    AcquirerEntity acquirer = safeAcquirer();
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "69-78", lineNumber);
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    PendingDebtEntity debt = new PendingDebtEntity();
    debt.setLineNumber(lineNumber);
    debt.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    debt.setCardNumber(FileParserUtils.extractStringLine(line, "3-19", lineNumber));
    debt.setNsu(FileParserUtils.extractLongLine(line, "19-31", lineNumber));
    debt.setDateOriginalTransaction(FileParserUtils.extractDateLine(line, "31-39", lineNumber));
    debt.setAuthorization(FileParserUtils.extractStringLine(line, "39-45", lineNumber));
    debt.setOriginalTransactionValue(optionalMoneyLine(line, "45-60", lineNumber, "transaction_value"));
    debt.setNumberRvOriginal(FileParserUtils.extractIntegerLine(line, "60-69", lineNumber));
    debt.setPvNumberOriginal(pvNumber);
    debt.setPvNumber(pvNumber);
    debt.setTid(FileParserUtils.extractStringLine(line, "78-98", lineNumber));
    debt.setProcessedFile(processedFile);
    debt.setAcquirer(acquirer);
    debt.setEstablishment(establishment);
    debt.setCompany(establishment != null ? establishment.getCompany() : null);
    return debt;
  }

  private SettledDebtEntity buildSettledDebtEcommerce(String line, int lineNumber, ProcessedFileEntity processedFile) {
    AcquirerEntity acquirer = safeAcquirer();
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "69-78", lineNumber);

    SettledDebtEntity debt = new SettledDebtEntity();
    debt.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    debt.setLineNumber(lineNumber);
    debt.setCardNumber(FileParserUtils.extractStringLine(line, "3-19", lineNumber));
    debt.setNsu(FileParserUtils.extractLongLine(line, "19-31", lineNumber));
    debt.setDateOriginalTransaction(FileParserUtils.extractDateLine(line, "31-39", lineNumber));
    debt.setAuthorization(FileParserUtils.extractStringLine(line, "39-45", lineNumber));
    debt.setOriginalTransactionValue(optionalMoneyLine(line, "45-60", lineNumber, "transaction_value"));
    debt.setNumberRvOriginal(FileParserUtils.extractIntegerLine(line, "60-69", lineNumber));
    debt.setPvNumberOriginal(pvNumber);
    debt.setPvNumber(pvNumber);
    debt.setTid(FileParserUtils.extractStringLine(line, "78-98", lineNumber));
    debt.setProcessedFile(processedFile);
    debt.setAcquirer(acquirer);
    return debt;
  }

  private InstallmentUnschedulingEntity buildInstallmentUnscheduling069(String line, int lineNumber, ProcessedFileEntity processedFile) {
    AcquirerEntity acquirer = safeAcquirer();
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    InstallmentUnschedulingEntity unscheduling = new InstallmentUnschedulingEntity();
    unscheduling.setAcquirer(acquirer);
    unscheduling.setLineNumber(lineNumber);
    unscheduling.setEcommerce(false);
    unscheduling.setUnschedulingStatus(STATUS_PENDING);
    unscheduling.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    unscheduling.setPvNumberOriginal(pvNumber);
    unscheduling.setRvNumberOriginal(FileParserUtils.extractIntegerLine(line, "12-21", lineNumber));
    unscheduling.setRvDateOriginal(FileParserUtils.extractDateLine(line, "21-29", lineNumber));
    unscheduling.setAdjustedPvNumber(FileParserUtils.extractIntegerLine(line, "29-38", lineNumber));
    unscheduling.setAdjustedRvNumber(FileParserUtils.extractIntegerLine(line, "38-47", lineNumber));
    unscheduling.setNewInstallmentValue(optionalMoneyLine(line, "47-62", lineNumber, "new_installment_value"));
    unscheduling.setOriginalValueChangedInstallment(optionalMoneyLine(line, "62-77", lineNumber, "original_installment_value"));
    unscheduling.setReferenceNumber(FileParserUtils.extractStringLine(line, "77-92", lineNumber));
    unscheduling.setAdjustedCreditDate(FileParserUtils.extractDateLine(line, "92-100", lineNumber));
    unscheduling.setAdjustmentValue(optionalMoneyLine(line, "100-115", lineNumber, "adjustment_value"));
    unscheduling.setOriginalInstallmentNumber(FileParserUtils.extractIntegerLine(line, "115-117", lineNumber));
    unscheduling.setCancellationDate(FileParserUtils.extractDateLine(line, "117-125", lineNumber));
    unscheduling.setCancellationValue(optionalMoneyLine(line, "125-140", lineNumber, "cancellation_value"));
    unscheduling.setCardNumber(FileParserUtils.extractStringLine(line, "140-156", lineNumber));
    unscheduling.setTransactionDate(FileParserUtils.extractDateLine(line, "156-164", lineNumber));
    unscheduling.setNsu(FileParserUtils.extractLongLine(line, "164-176", lineNumber));
    unscheduling.setTypeDebit(FileParserUtils.extractStringLine(line, "176-177", lineNumber));
    unscheduling.setAdjustedInstallmentNumber(FileParserUtils.extractIntegerLine(line, "177-179", lineNumber));
    unscheduling.setFlagRvAdjusted(safeFlag(acquirer, trim(FileParserUtils.extractStringLine(line, "179-180", lineNumber))));
    unscheduling.setAdjustedRvDate(FileParserUtils.extractDateLine(line, "180-188", lineNumber));
    unscheduling.setNegotiationType(FileParserUtils.extractIntegerLine(line, "188-189", lineNumber));
    unscheduling.setNegotiationContractNumber(FileParserUtils.extractLongLine(line, "189-206", lineNumber));
    unscheduling.setPartnerCnpj(FileParserUtils.extractStringLine(line, "206-221", lineNumber));
    unscheduling.setNegotiationDate(FileParserUtils.extractDateLine(line, "221-229", lineNumber));
    unscheduling.setProcessedFile(processedFile);
    unscheduling.setEstablishment(establishment);
    unscheduling.setCompany(establishment != null ? establishment.getCompany() : null);
    return unscheduling;
  }

  private RedeNegotiatedTransactionEntity buildNegotiatedTransaction(String line, int lineNumber, ProcessedFileEntity processedFile) {
    AcquirerEntity acquirer = safeAcquirer();
    Integer establishmentNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    EstablishmentEntity establishment = safeEstablishment(establishmentNumber);
    String flagCode = trim(FileParserUtils.extractStringLine(line, "75-76", lineNumber));

    RedeNegotiatedTransactionEntity transaction = new RedeNegotiatedTransactionEntity();
    transaction.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    transaction.setLineNumber(lineNumber);
    transaction.setEstablishmentNumber(establishmentNumber);
    transaction.setSettlementSummaryNumber(FileParserUtils.extractLongLine(line, "12-23", lineNumber));
    transaction.setSettlementSummaryValue(optionalMoneyLine(line, "23-40", lineNumber, "reference_credit_order_value"));
    transaction.setRvCreditDate(FileParserUtils.extractDateLine(line, "40-48", lineNumber));
    transaction.setRvNumber(FileParserUtils.extractIntegerLine(line, "57-66", lineNumber));
    transaction.setSaleDate(FileParserUtils.extractDateLine(line, "66-74", lineNumber));
    transaction.setTransactionType(FileParserUtils.extractStringLine(line, "74-75", lineNumber));
    transaction.setFlagCode(flagCode);
    transaction.setNegotiationType(FileParserUtils.extractIntegerLine(line, "76-77", lineNumber));
    transaction.setNegotiationContractNumber(FileParserUtils.extractLongLine(line, "77-94", lineNumber));
    transaction.setPartnerCnpj(FileParserUtils.extractStringLine(line, "94-109", lineNumber));
    transaction.setGeneratedRlDocumentNumber(FileParserUtils.extractLongLine(line, "109-120", lineNumber));
    transaction.setNegotiatedValue(optionalMoneyLine(line, "120-137", lineNumber, "negotiated_value"));
    transaction.setNegotiationDate(FileParserUtils.extractDateLine(line, "137-145", lineNumber));
    transaction.setLiquidationDate(FileParserUtils.extractDateLine(line, "145-153", lineNumber));
    transaction.setBank(FileParserUtils.extractIntegerLine(line, "153-156", lineNumber));
    transaction.setAgency(FileParserUtils.extractIntegerLine(line, "156-162", lineNumber));
    transaction.setAccount(FileParserUtils.extractLongLine(line, "162-173", lineNumber));
    transaction.setCreditStatus(FileParserUtils.extractIntegerLine(line, "173-175", lineNumber));
    transaction.setProcessedFile(processedFile);
    transaction.setAcquirer(acquirer);
    transaction.setFlag(safeFlag(acquirer, flagCode));
    transaction.setEstablishment(establishment);
    transaction.setCompany(establishment != null ? establishment.getCompany() : null);
    transaction.setSalesSummary(safeSalesSummary(acquirer, establishmentNumber, transaction.getRvNumber()));
    return transaction;
  }

  private RedePixCancellationEntity buildPixCancellation(String line, int lineNumber, ProcessedFileEntity processedFile) {
    AcquirerEntity acquirer = safeAcquirer();
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    RedePixCancellationEntity cancellation = new RedePixCancellationEntity();
    cancellation.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    cancellation.setLineNumber(lineNumber);
    cancellation.setPvNumber(pvNumber);
    cancellation.setDebitOrderNumber(FileParserUtils.extractLongLine(line, "12-23", lineNumber));
    cancellation.setInternalChargeId(FileParserUtils.extractStringLine(line, "23-43", lineNumber));
    cancellation.setProcessedFile(processedFile);
    cancellation.setAcquirer(acquirer);
    cancellation.setEstablishment(establishment);
    cancellation.setCompany(establishment != null ? establishment.getCompany() : null);
    return cancellation;
  }

  private RedeSuspendedPaymentEntity buildSuspendedPayment(String line, int lineNumber, ProcessedFileEntity processedFile) {
    AcquirerEntity acquirer = safeAcquirer();
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String flagCode = trim(FileParserUtils.extractStringLine(line, "82-83", lineNumber));

    RedeSuspendedPaymentEntity payment = new RedeSuspendedPaymentEntity();
    payment.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    payment.setLineNumber(lineNumber);
    payment.setPvNumber(pvNumber);
    payment.setCreditOrderNumber(FileParserUtils.extractLongLine(line, "12-23", lineNumber));
    payment.setCreditOrderValue(optionalMoneyLine(line, "23-40", lineNumber, "credit_order_value"));
    payment.setReleaseDate(FileParserUtils.extractDateLine(line, "40-48", lineNumber));
    payment.setOriginalDueDate(FileParserUtils.extractDateLine(line, "48-56", lineNumber));
    payment.setRvNumber(FileParserUtils.extractIntegerLine(line, "56-65", lineNumber));
    payment.setRvDate(FileParserUtils.extractDateLine(line, "65-73", lineNumber));
    payment.setSuspensionDate(FileParserUtils.extractDateLine(line, "73-81", lineNumber));
    payment.setPaymentType(FileParserUtils.extractStringLine(line, "81-82", lineNumber));
    payment.setFlagCode(flagCode);
    payment.setRedeContractNumber(FileParserUtils.extractLongLine(line, "83-100", lineNumber));
    payment.setContractUpdateDate(FileParserUtils.extractDateLine(line, "100-108", lineNumber));
    payment.setInstallmentNumber(FileParserUtils.extractIntegerLine(line, "108-110", lineNumber));
    payment.setOriginalContractDate(FileParserUtils.extractDateLine(line, "110-118", lineNumber));
    payment.setCipContractNumber(FileParserUtils.extractStringLine(line, "118-137", lineNumber));
    payment.setProcessedFile(processedFile);
    payment.setAcquirer(acquirer);
    payment.setFlag(safeFlag(acquirer, flagCode));
    payment.setEstablishment(establishment);
    payment.setCompany(establishment != null ? establishment.getCompany() : null);
    payment.setSalesSummary(safeSalesSummary(acquirer, pvNumber, payment.getRvNumber()));
    return payment;
  }

  private RedeTechnicalReserveEntity buildTechnicalReserve(String line, int lineNumber, ProcessedFileEntity processedFile) {
    AcquirerEntity acquirer = safeAcquirer();
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "3-12", lineNumber);
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String flagCode = trim(FileParserUtils.extractStringLine(line, "29-30", lineNumber));

    RedeTechnicalReserveEntity reserve = new RedeTechnicalReserveEntity();
    reserve.setRecordType(FileParserUtils.extractStringLine(line, "0-3", lineNumber));
    reserve.setLineNumber(lineNumber);
    reserve.setPvNumber(pvNumber);
    reserve.setRvNumberOriginal(FileParserUtils.extractIntegerLine(line, "12-21", lineNumber));
    reserve.setRvDateOriginal(FileParserUtils.extractDateLine(line, "21-29", lineNumber));
    reserve.setFlagCode(flagCode);
    reserve.setInstallmentNumber(FileParserUtils.extractIntegerLine(line, "30-32", lineNumber));
    reserve.setDueDate(FileParserUtils.extractDateLine(line, "32-40", lineNumber));
    reserve.setCreditOrderNumber(FileParserUtils.extractLongLine(line, "40-51", lineNumber));
    reserve.setCreditOrderReferenceNumber(FileParserUtils.extractLongLine(line, "51-62", lineNumber));
    reserve.setCreditOrderValue(optionalMoneyLine(line, "62-77", lineNumber, "credit_order_value"));
    reserve.setReserveInclusionDate(FileParserUtils.extractDateLine(line, "77-85", lineNumber));
    reserve.setReserveExclusionDate(FileParserUtils.extractDateLine(line, "85-93", lineNumber));
    reserve.setBank(FileParserUtils.extractIntegerLine(line, "93-96", lineNumber));
    reserve.setAgency(FileParserUtils.extractIntegerLine(line, "96-102", lineNumber));
    reserve.setAccount(FileParserUtils.extractLongLine(line, "102-113", lineNumber));
    reserve.setReserveStatus(FileParserUtils.extractIntegerLine(line, "113-114", lineNumber));
    reserve.setProcessedFile(processedFile);
    reserve.setAcquirer(acquirer);
    reserve.setFlag(safeFlag(acquirer, flagCode));
    reserve.setEstablishment(establishment);
    reserve.setCompany(establishment != null ? establishment.getCompany() : null);
    reserve.setSalesSummary(safeSalesSummary(acquirer, pvNumber, reserve.getRvNumberOriginal()));
    return reserve;
  }


  private String resolveAdjustmentAcquirerCode(String line, int lineNumber, String identifier) {
    if ("035".equals(identifier)) {
      return trim(FileParserUtils.extractStringLine(line, "299-300", lineNumber));
    }
    if ("038".equals(identifier)) {
      return trim(FileParserUtils.extractStringLine(line, "302-303", lineNumber));
    }
    if ("043".equals(identifier)) {
      return trim(FileParserUtils.extractStringLine(line, "114-115", lineNumber));
    }
    return trim(FileParserUtils.extractStringLine(line, "299-300", lineNumber));
  }

  private AcquirerEntity safeAcquirer() {
    try {
      return lookupService.acquirerByIdentifier("REDE");
    } catch (Exception ex) {
      log.debug("Adquirente REDE não localizada para EEFI.", ex);
      return null;
    }
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    if (pvNumber == null || pvNumber <= 0) return null;
    try {
      return lookupService.establishmentByPvNumber(pvNumber);
    } catch (Exception ex) {
      log.debug("Estabelecimento não localizado para EEFI. pvNumber={}", pvNumber);
      return null;
    }
  }

  private FlagEntity safeFlag(AcquirerEntity acquirer, String code) {
    String normalizedCode = normalizeFlagCode(code);
    if (acquirer == null || normalizedCode == null) return null;

    try {
      return lookupService.flagByAcquirerCode(acquirer, normalizedCode);
    } catch (Exception ex) {
      log.debug("Flag Rede não localizada para EEFI. acquirerId={}, code={}",
        acquirer.getId(), normalizedCode);
      return null;
    }
  }

  private SalesSummaryEntity safeSalesSummary(AcquirerEntity acquirer, Integer pvNumber, Integer rvNumber) {
    if (acquirer == null || acquirer.getId() == null || pvNumber == null || rvNumber == null) return null;
    return salesSummaryRepository
      .findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(acquirer.getId(), pvNumber, rvNumber)
      .orElse(null);
  }

  private BankingDomicileEntity safeDomicile(Integer agency, Integer currentAccount, CompanyEntity company) {
    if (agency == null || currentAccount == null) return null;
    if (company != null && company.getId() != null) {
      return bankingDomicileRepository
        .findFirstByAgencyAndCurrentAccountAndCompany_Id(agency, currentAccount, company.getId())
        .orElseGet(() -> bankingDomicileRepository.findFirstByAgencyAndCurrentAccount(agency, currentAccount).orElse(null));
    }
    return bankingDomicileRepository.findFirstByAgencyAndCurrentAccount(agency, currentAccount).orElse(null);
  }

  private String normalizeFlagCode(String code) {
    String value = trim(code);
    if (value == null || value.isBlank()) return null;

    // No EEFI, o código de bandeira pode ser numérico ou alfanumérico de 1 caractere.
    // Quando o layout traz texto no range lido (ex.: "D3", "PO", "-I"), não chamamos lookupService,
    // pois exceções de services transacionais podem marcar a transação como rollback-only.
    if (!value.matches("[A-Za-z0-9]{1,2}")) return null;

    return value;
  }

  private Integer optionalIntegerLine(String line, String range, int lineNumber) {
    String raw = rawRange(line, range);
    if (raw == null || raw.isBlank() || !raw.matches("\\d+")) return null;
    return FileParserUtils.extractIntegerLine(line, range, lineNumber);
  }

  private Long optionalLongLine(String line, String range, int lineNumber) {
    String raw = rawRange(line, range);
    if (raw == null || raw.isBlank() || !raw.matches("\\d+")) return null;
    return FileParserUtils.extractLongLine(line, range, lineNumber);
  }

  private BigDecimal optionalMoneyLine(String line, String range, int lineNumber) {
    return optionalMoneyLine(line, range, lineNumber, null);
  }

  private BigDecimal optionalMoneyLine(String line, String range, int lineNumber, String fieldName) {
    String raw = rawRange(line, range);
    if (raw == null || raw.isBlank() || !raw.matches("\\d+")) return null;

    BigDecimal value = FileParserUtils.extractBigDecimalLine(line, range, lineNumber);
    if (value == null) return null;

    if (value.abs().compareTo(MAX_SAFE_MONEY) > 0) {
      RedeProcessingWarningCollector collector = warningCollector.get();
      if (collector != null) {
        collector.monetaryOutOfLimit(lineNumber, fieldName == null ? "money" : fieldName, "adjustment", range, value);
      } else {
        log.debug("EEFI valor monetário fora do limite ignorado. linha={}, campo={}, range={}, valor={}",
          lineNumber, fieldName == null ? "money" : fieldName, range, value);
      }
      return null;
    }

    return value;
  }

  private java.time.LocalDate optionalDateLine(String line, String range, int lineNumber) {
    String raw = rawRange(line, range);
    if (raw == null || raw.isBlank() || !raw.matches("\\d{8}")) return null;
    return FileParserUtils.extractDateLine(line, range, lineNumber);
  }

  private BigDecimal nullToZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String rawRange(String line, String range) {
    if (line == null || range == null || !range.contains("-")) return null;

    String[] parts = range.split("-", 2);
    try {
      int start = Integer.parseInt(parts[0]);
      int end = Integer.parseInt(parts[1]);
      if (start < 0 || end <= start || start >= line.length()) return null;
      return line.substring(start, Math.min(end, line.length())).trim();
    } catch (NumberFormatException ex) {
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
