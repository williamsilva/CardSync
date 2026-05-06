package com.cardsync.core.file.service;

import com.cardsync.core.file.bank.BankingDomicileResolver;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.MoveFileService;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.*;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import com.cardsync.domain.repository.InstallmentUnschedulingRepository;
import com.cardsync.domain.repository.ProcessedFileRepository;
import com.cardsync.domain.repository.RedeEeVdTotalizerRepository;
import com.cardsync.domain.repository.RedeIcPlusTransactionRepository;
import com.cardsync.domain.repository.RedeNegotiatedTransactionRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRedeEeVdService {
  private static final int STATUS_PENDING = StatusPaymentEnum.PENDING.getCode();
  private static final int TRANSACTION_PENDING = StatusTransactionEnum.PENDING.getCode();
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

  private static final Set<String> SUPPORTED_IDENTIFIERS = Set.of(
    "00", "01", "02", "03", "04", "05", "08", "09", "11", "13", "17", "18", "19", "20"
  );

  private final FileLookupService lookupService;
  private final MoveFileService moveFileService;
  private final BankingDomicileResolver bankingDomicileResolver;
  private final ProcessedFileRepository processedFileRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final AdjustmentRepository adjustmentRepository;
  private final InstallmentUnschedulingRepository installmentUnschedulingRepository;
  private final RedeEeVdTotalizerRepository redeEeVdTotalizerRepository;
  private final RedeNegotiatedTransactionRepository redeNegotiatedTransactionRepository;
  private final RedeIcPlusTransactionRepository redeIcPlusTransactionRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths) {
    ProcessedFileEntity processedFile = null;
    try {
      log.info("▶ Iniciando processamento Rede EEVD: {}", file.getFileName());
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      processedFile = createProcessedFile(file, lines.size());
      processedFile.markProcessing();

      List<SalesSummaryEntity> summaries = new ArrayList<>();
      List<TransactionAcqEntity> transactions = new ArrayList<>();
      List<InstallmentAcqEntity> installments = new ArrayList<>();
      List<AdjustmentEntity> adjustments = new ArrayList<>();
      List<InstallmentUnschedulingEntity> unschedulings = new ArrayList<>();
      List<RedeEeVdTotalizerEntity> totalizers = new ArrayList<>();
      List<RedeNegotiatedTransactionEntity> negotiatedTransactions = new ArrayList<>();
      List<RedeIcPlusTransactionEntity> icPlusTransactions = new ArrayList<>();
      Map<String, Integer> countsByIdentifier = new TreeMap<>();
      Set<String> unidentified = new TreeSet<>();

      int recognized = 0;
      int ignored = 0;
      int warnings = 0;

      for (int i = 0; i < lines.size(); i++) {
        int lineNumber = i + 1;
        String line = lines.get(i);
        List<String> columns = splitCsv(line);
        String identifier = col(columns, 0);

        if (identifier == null || identifier.isBlank()) {
          ignored++;
          warnings++;
          processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
            "REDE_EEVD_EMPTY_IDENTIFIER", "Linha EEVD sem identificador de registro.", line));
          continue;
        }

        if (!SUPPORTED_IDENTIFIERS.contains(identifier)) {
          ignored++;
          warnings++;
          unidentified.add(identifier);
          processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
            "REDE_EEVD_UNSUPPORTED_IDENTIFIER", "Identificador EEVD não mapeado: " + identifier, line));
          continue;
        }

        recognized++;
        countsByIdentifier.merge(identifier, 1, Integer::sum);

        switch (identifier) {
          case "00" -> applyHeader(columns, processedFile);
          case "01" -> summaries.add(buildSalesSummary(columns, lineNumber, processedFile));
          case "05" -> addTransactionWithInstallment(transactions, installments,
            buildTransaction05(columns, lineNumber, processedFile, summaries));
          case "08" -> unschedulings.add(buildUnscheduling08(columns, lineNumber, processedFile));
          case "11" -> adjustments.add(buildAdjustment11(columns, lineNumber, processedFile, summaries));
          case "13" -> addTransactionWithInstallment(transactions, installments,
            buildTransaction13(columns, lineNumber, processedFile, summaries));
          case "17" -> adjustments.add(buildAdjustment17(columns, lineNumber, processedFile, summaries));
          case "18" -> negotiatedTransactions.add(buildNegotiatedTransaction18(columns, lineNumber, processedFile, summaries));
          case "19" -> icPlusTransactions.add(buildIcPlusTransaction19(columns, lineNumber, processedFile, summaries));
          case "20" -> addTransactionWithInstallment(transactions, installments,
            buildTransaction20(columns, lineNumber, processedFile, summaries));
          case "02", "03", "04" -> totalizers.add(buildTotalizer(columns, lineNumber, processedFile));
          case "09" -> {
            // Registro reconhecido. Pré-datadas liquidadas será persistido em lote posterior, se entrar no escopo operacional.
          }
          default -> ignored++;
        }
      }

      processedFile.setProcessedLines(recognized);
      processedFile.setIgnoredLines(ignored);
      processedFile.setWarningLines(warnings);
      processedFile.setErrorLines(0);
      processedFile.markFinished(warnings > 0 ? FileStatusEnum.PROCESSED_WITH_WARNINGS : FileStatusEnum.PROCESSED,
        "linhas=" + processedFile.getTotalLines()
          + ", reconhecidas=" + recognized
          + ", resumos=" + summaries.size()
          + ", transacoes=" + transactions.size()
          + ", parcelas=" + installments.size()
          + ", ajustes=" + adjustments.size()
          + ", desagendamentos=" + unschedulings.size()
          + ", totalizadores=" + totalizers.size()
          + ", negociadas=" + negotiatedTransactions.size()
          + ", icPlus=" + icPlusTransactions.size()
          + ", ignoradas=" + ignored
          + ", avisos=" + warnings
          + ", registros=" + countsByIdentifier);

      processedFileRepository.save(processedFile);
      salesSummaryRepository.saveAll(summaries);
      transactionAcqRepository.saveAll(transactions);
      installmentAcqRepository.saveAll(installments);
      adjustmentRepository.saveAll(adjustments);
      installmentUnschedulingRepository.saveAll(unschedulings);
      redeEeVdTotalizerRepository.saveAll(totalizers);
      redeNegotiatedTransactionRepository.saveAll(negotiatedTransactions);
      redeIcPlusTransactionRepository.saveAll(icPlusTransactions);
      moveFileService.moveAfterCommit(file, paths.getProcessed());

      if (!unidentified.isEmpty()) {
        log.warn("⚠ EEVD {} possui identificadores não mapeados: {}", file.getFileName(), unidentified);
      }
      log.info("✅ EEVD {} finalizado: status={}, {}", file.getFileName(), processedFile.getStatus(), processedFile.getStatusMessage());
    } catch (DataIntegrityViolationException ex) {
      log.error("⚠ Arquivo EEVD {} já processado anteriormente.", file.getFileName());
      if (processedFile != null) processedFile.setStatus(FileStatusEnum.DUPLICATE);
      moveFileService.moveAfterRollback(file, paths.getDuplicate());
      throw ex;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar EEVD {}: {}", file.getFileName(), safeMessage(ex), ex);
      if (processedFile != null) {
        processedFile.setStatus(FileStatusEnum.ERROR);
        processedFile.setErrorMessage(safeMessage(ex));
      }
      moveFileService.moveAfterRollback(file, paths.getError());
      throw new IllegalStateException(ex);
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
    processedFile.setTypeFile("Rede EEVD - Extrato de vendas débito");
    processedFile.setVersion("EEVD");
    processedFile.setTotalLines(totalLines);
    return processedFile;
  }

  private void applyHeader(List<String> columns, ProcessedFileEntity processedFile) {
    processedFile.setPvGroupNumber(toInteger(col(columns, 1)));
    processedFile.setDateFile(parseDate(col(columns, 2)));
    processedFile.setTypeFile(defaultIfBlank(col(columns, 4), "Movimentação diária - Cartões de Débito"));
    processedFile.setCommercialName(col(columns, 6));
    processedFile.setVersion(defaultIfBlank(col(columns, 9), "EEVD"));
  }

  private SalesSummaryEntity buildSalesSummary(List<String> c, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = toInteger(col(c, 1));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    CompanyEntity company = establishment != null ? establishment.getCompany() : null;
    String flagCode = col(c, 13);
    Integer agency = toInteger(col(c, 11));
    Integer currentAccount = toInteger(col(c, 12));

    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setRecordType(col(c, 0));
    summary.setLineNumber(lineNumber);
    summary.setPvNumber(pvNumber);
    summary.setRvNumber(toInteger(col(c, 4)));
    summary.setNumberCvNsu(toInteger(col(c, 5)));
    summary.setGrossValue(money(col(c, 6)));
    summary.setDiscountValue(money(col(c, 7)));
    summary.setLiquidValue(money(col(c, 8)));
    summary.setSummaryType(col(c, 9));
    summary.setBank(toInteger(col(c, 10)));
    summary.setAgency(agency);
    summary.setCurrentAccount(currentAccount);
    summary.setTipValue(BigDecimal.ZERO);
    summary.setRejectedValue(BigDecimal.ZERO);
    summary.setAdjustedValue(BigDecimal.ZERO);
    summary.setManualGenerated(false);
    summary.setRvDate(parseDate(col(c, 3)));
    summary.setFirstInstallmentCreditDate(parseDate(col(c, 2)));
    summary.setStatusPaymentBank(STATUS_PENDING);
    summary.setCreditOrderStatus(STATUS_PENDING);
    summary.setTransactionsStatus(TRANSACTION_PENDING);
    summary.setModality(resolveSummaryModality(col(c, 9)));
    summary.setAcquirer(acquirer);
    summary.setCompany(company);
    summary.setBankingDomicile(safeBankingDomicile(agency, currentAccount, company));
    summary.setFlag(safeFlag(acquirer, flagCode));
    summary.setProcessedFile(processedFile);
    return summary;
  }

  private TransactionAcqEntity buildTransaction05(List<String> c, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    Integer pvNumber = toInteger(col(c, 1));
    Integer rvNumber = toInteger(col(c, 2));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    SalesSummaryEntity summary = findSummary(summaries, pvNumber, rvNumber);
    BigDecimal gross = money(col(c, 4));
    BigDecimal discount = money(col(c, 5));

    TransactionAcqEntity tx = baseTransaction(c, lineNumber, processedFile, pvNumber, rvNumber, acquirer, establishment, summary);
    tx.setGrossValue(gross);
    tx.setDiscountValue(discount);
    tx.setLiquidValue(money(col(c, 6)));
    tx.setCardNumber(col(c, 7));
    tx.setTransactionType(col(c, 8));
    tx.setNsu(toLong(col(c, 9)));
    tx.setCreditDate(parseDate(col(c, 10)));
    tx.setSaleDate(toOffsetDateTime(parseDate(col(c, 3)), col(c, 12)));
    tx.setStatusCv(col(c, 11));
    tx.setMachine(col(c, 13));
    tx.setCapture(resolveCapture(col(c, 14)));
    tx.setDccCurrency(col(c, 15));
    tx.setPurchaseValue(money(col(c, 16)));
    tx.setWithdrawalValue(money(col(c, 17)));
    tx.setFlag(safeFlag(acquirer, col(c, 18)));
    tx.setAuthorization(col(c, 19));
    tx.setServiceCode(col(c, 20));
    tx.setFirstInstallmentValue(tx.getLiquidValue());
    tx.setMdrRate(calculateRate(gross, discount));
    return tx;
  }

  private AdjustmentEntity buildAdjustment11(List<String> c, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    Integer pvAdjusted = toInteger(col(c, 1));
    Integer rvAdjusted = toInteger(col(c, 2));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvAdjusted);
    SalesSummaryEntity summary = findSummary(summaries, pvAdjusted, rvAdjusted);

    AdjustmentEntity adjustment = baseAdjustment(c, lineNumber, processedFile, acquirer, establishment, summary, false);
    adjustment.setPvNumber(pvAdjusted);
    adjustment.setPvNumberAdjustment(pvAdjusted);
    adjustment.setRvNumberAdjustment(rvAdjusted);
    adjustment.setAdjustmentDate(parseDate(col(c, 3)));
    adjustment.setAdjustmentValue(money(col(c, 4)));
    adjustment.setAdjustmentType(col(c, 5));
    adjustment.setAdjustmentReason(toInteger(col(c, 6)));
    adjustment.setAdjustmentDescription(col(c, 7));
    adjustment.setCardNumber(col(c, 8));
    adjustment.setTransactionDate(parseDate(col(c, 9)));
    adjustment.setRvNumberOriginal(toInteger(col(c, 10)));
    adjustment.setLetterNumber(toLong(col(c, 11)));
    adjustment.setLetterReference(col(c, 11));
    adjustment.setLetterDate(parseDate(col(c, 12)));
    adjustment.setReferenceMonth(col(c, 13));
    adjustment.setPvNumberOriginal(toInteger(col(c, 14)));
    adjustment.setRvDateOriginal(parseDate(col(c, 15)));
    adjustment.setTransactionValue(money(col(c, 16)));
    adjustment.setNet(col(c, 17));
    adjustment.setCreditDate(parseDate(col(c, 18)));
    adjustment.setOriginalGrossSalesSummaryValue(money(col(c, 19)));
    adjustment.setCancellationValueRequested(money(col(c, 20)));
    adjustment.setNsu(toLong(col(c, 21)));
    adjustment.setAuthorization(col(c, 22));
    adjustment.setDebitType(col(c, 23));
    adjustment.setNumberDebitOrder(toLong(col(c, 24)));
    adjustment.setTotalDebitValue(money(col(c, 25)));
    adjustment.setPendingValue(money(col(c, 26)));
    adjustment.setRvFlagOrigin(safeFlag(acquirer, col(c, 27)));
    adjustment.setRvFlagAdjustment(safeFlag(acquirer, col(c, 28)));
    adjustment.setAdjustmentReason2(toInteger(col(c, 29)));
    adjustment.setRawAdjustmentCode(col(c, 29));
    return adjustment;
  }

  private TransactionAcqEntity buildTransaction13(List<String> c, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    Integer pvNumber = toInteger(col(c, 1));
    Integer rvNumber = toInteger(col(c, 2));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    SalesSummaryEntity summary = findSummary(summaries, pvNumber, rvNumber);

    TransactionAcqEntity tx = baseTransaction(c, lineNumber, processedFile, pvNumber, rvNumber, acquirer, establishment, summary);
    tx.setGrossValue(money(col(c, 4)));
    tx.setDiscountValue(BigDecimal.ZERO);
    tx.setLiquidValue(tx.getGrossValue());
    tx.setCardNumber(col(c, 5));
    tx.setNsu(toLong(col(c, 6)));
    tx.setSaleDate(toOffsetDateTime(parseDate(col(c, 3)), null));
    tx.setCapture(CaptureEnum.ECOMMERCE.getCode());
    tx.setTid(col(c, 7));
    tx.setReferenceNumber(col(c, 8));
    tx.setFirstInstallmentValue(tx.getLiquidValue());
    return tx;
  }

  private AdjustmentEntity buildAdjustment17(List<String> c, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    Integer rvOriginal = toInteger(col(c, 3));
    Integer pvOriginal = toInteger(col(c, 4));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvOriginal);
    SalesSummaryEntity summary = findSummary(summaries, pvOriginal, rvOriginal);

    AdjustmentEntity adjustment = baseAdjustment(c, lineNumber, processedFile, acquirer, establishment, summary, true);
    adjustment.setCardNumber(col(c, 1));
    adjustment.setTransactionDate(parseDate(col(c, 2)));
    adjustment.setRvNumberOriginal(rvOriginal);
    adjustment.setPvNumberOriginal(pvOriginal);
    adjustment.setPvNumber(pvOriginal);
    adjustment.setRvDateOriginal(parseDate(col(c, 5)));
    adjustment.setTransactionValue(money(col(c, 6)));
    adjustment.setNsu(toLong(col(c, 7)));
    adjustment.setAuthorization(col(c, 8));
    adjustment.setTid(col(c, 9));
    adjustment.setEcommerceOrderNumber(col(c, 10));
    return adjustment;
  }

  private AdjustmentEntity baseAdjustment(List<String> c, int lineNumber, ProcessedFileEntity processedFile,
                                          AcquirerEntity acquirer, EstablishmentEntity establishment,
                                          SalesSummaryEntity summary, boolean ecommerce) {
    AdjustmentEntity adjustment = new AdjustmentEntity();
    adjustment.setRecordType(col(c, 0));
    adjustment.setSourceRecordIdentifier(col(c, 0));
    adjustment.setLineNumber(lineNumber);
    adjustment.setEcommerce(ecommerce);
    adjustment.setAcquirer(acquirer);
    adjustment.setEstablishment(establishment);
    adjustment.setCompany(establishment != null ? establishment.getCompany() : null);
    adjustment.setSalesSummary(summary);
    adjustment.setProcessedFile(processedFile);
    return adjustment;
  }

  private InstallmentUnschedulingEntity buildUnscheduling08(List<String> c, int lineNumber, ProcessedFileEntity processedFile) {
    Integer pvNumber = toInteger(col(c, 1));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);

    InstallmentUnschedulingEntity unscheduling = new InstallmentUnschedulingEntity();
    unscheduling.setRecordType(col(c, 0));
    unscheduling.setLineNumber(lineNumber);
    unscheduling.setPvNumberOriginal(pvNumber);
    unscheduling.setRvNumberOriginal(toInteger(col(c, 2)));
    unscheduling.setTransactionDate(parseDate(col(c, 3)));
    unscheduling.setNsu(toLong(col(c, 4)));
    unscheduling.setRvValueOriginal(money(col(c, 5)));
    unscheduling.setCancellationValue(money(col(c, 6)));
    unscheduling.setUnschedulingStatus(toInteger(col(c, 7)));
    unscheduling.setDateCredit(parseDate(col(c, 8)));
    unscheduling.setNewInstallmentValue(money(col(c, 9)));
    unscheduling.setTypeDebit(col(c, 10));
    unscheduling.setEcommerce(false);
    unscheduling.setAcquirer(acquirer);
    unscheduling.setEstablishment(establishment);
    unscheduling.setCompany(establishment != null ? establishment.getCompany() : null);
    unscheduling.setProcessedFile(processedFile);
    return unscheduling;
  }

  private RedeEeVdTotalizerEntity buildTotalizer(List<String> c, int lineNumber, ProcessedFileEntity processedFile) {
    String recordType = col(c, 0);
    Integer number = toInteger(col(c, 1));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = "02".equals(recordType) ? safeEstablishment(number) : null;
    CompanyEntity company = establishment != null ? establishment.getCompany() : null;

    RedeEeVdTotalizerEntity totalizer = new RedeEeVdTotalizerEntity();
    totalizer.setRecordType(recordType);
    totalizer.setLineNumber(lineNumber);
    totalizer.setPvNumber("02".equals(recordType) ? number : null);
    totalizer.setMatrixNumber("03".equals(recordType) || "04".equals(recordType) ? number : null);
    totalizer.setSalesSummaryQuantity(toInteger(col(c, 2)));
    totalizer.setSalesReceiptQuantity(toInteger(col(c, 3)));
    totalizer.setTotalGrossValue(money(col(c, 4)));
    totalizer.setTotalDiscountValue(money(col(c, 5)));
    totalizer.setTotalLiquidValue(money(col(c, 6)));
    totalizer.setPredatingGrossValue(money(col(c, 7)));
    totalizer.setPredatingDiscountValue(money(col(c, 8)));
    totalizer.setPredatingLiquidValue(money(col(c, 9)));
    totalizer.setTotalFileRecords("04".equals(recordType) ? toInteger(col(c, 10)) : null);
    totalizer.setAcquirer(acquirer);
    totalizer.setEstablishment(establishment);
    totalizer.setCompany(company);
    totalizer.setProcessedFile(processedFile);
    return totalizer;
  }

  private RedeNegotiatedTransactionEntity buildNegotiatedTransaction18(List<String> c, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    Integer pvNumber = toInteger(col(c, 1));
    Integer rvNumber = toInteger(col(c, 2));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    SalesSummaryEntity summary = findSummary(summaries, pvNumber, rvNumber);

    RedeNegotiatedTransactionEntity negotiated = new RedeNegotiatedTransactionEntity();
    negotiated.setRecordType(col(c, 0));
    negotiated.setLineNumber(lineNumber);
    negotiated.setEstablishmentNumber(pvNumber);
    negotiated.setRvNumber(rvNumber);
    negotiated.setSaleDate(parseDate(col(c, 3)));
    negotiated.setRvCreditDate(parseDate(col(c, 4)));
    negotiated.setTransactionType(col(c, 5));
    negotiated.setFlagCode(col(c, 6));
    negotiated.setFlag(safeFlag(acquirer, col(c, 6)));
    negotiated.setNegotiationType(toInteger(col(c, 7)));
    negotiated.setSettlementSummaryNumber(toLong(col(c, 8)));
    negotiated.setSettlementSummaryDate(parseDate(col(c, 9)));
    negotiated.setSettlementSummaryValue(money(col(c, 10)));
    negotiated.setNegotiationContractNumber(toLong(col(c, 11)));
    negotiated.setPartnerCnpj(col(c, 12));
    negotiated.setGeneratedRlDocumentNumber(toLong(col(c, 13)));
    negotiated.setNegotiatedValue(money(col(c, 14)));
    negotiated.setNegotiationDate(parseDate(col(c, 15)));
    negotiated.setLiquidationDate(parseDate(col(c, 16)));
    negotiated.setBank(toInteger(col(c, 17)));
    negotiated.setAgency(toInteger(col(c, 18)));
    negotiated.setAccount(toLong(col(c, 19)));
    negotiated.setCreditStatus(toInteger(col(c, 20)));
    negotiated.setAcquirer(acquirer);
    negotiated.setEstablishment(establishment);
    negotiated.setCompany(establishment != null ? establishment.getCompany() : null);
    negotiated.setSalesSummary(summary);
    negotiated.setProcessedFile(processedFile);
    return negotiated;
  }

  private RedeIcPlusTransactionEntity buildIcPlusTransaction19(List<String> c, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    Integer pvNumber = toInteger(col(c, 1));
    Integer rvNumber = toInteger(col(c, 2));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    SalesSummaryEntity summary = findSummary(summaries, pvNumber, rvNumber);

    RedeIcPlusTransactionEntity icPlus = new RedeIcPlusTransactionEntity();
    icPlus.setRecordType(col(c, 0));
    icPlus.setLineNumber(lineNumber);
    icPlus.setPvNumber(pvNumber);
    icPlus.setRvNumberOriginal(rvNumber);
    icPlus.setRvDateOriginal(parseDate(col(c, 3)));
    icPlus.setNsu(toLong(col(c, 4)));
    icPlus.setTransactionDate(parseDate(col(c, 5)));
    icPlus.setMcc(toInteger(col(c, 6)));
    icPlus.setInterchangeValue(money(col(c, 7)));
    icPlus.setPlusValue(money(col(c, 8)));
    icPlus.setEntryMode(col(c, 9));
    icPlus.setAcquirer(acquirer);
    icPlus.setEstablishment(establishment);
    icPlus.setCompany(establishment != null ? establishment.getCompany() : null);
    icPlus.setSalesSummary(summary);
    icPlus.setProcessedFile(processedFile);
    return icPlus;
  }


  private TransactionAcqEntity buildTransaction20(List<String> c, int lineNumber, ProcessedFileEntity processedFile, List<SalesSummaryEntity> summaries) {
    Integer pvNumber = toInteger(col(c, 1));
    Integer rvNumber = toInteger(col(c, 2));
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    SalesSummaryEntity summary = findSummary(summaries, pvNumber, rvNumber);

    TransactionAcqEntity tx = baseTransaction(c, lineNumber, processedFile, pvNumber, rvNumber, acquirer, establishment, summary);
    tx.setGrossValue(money(col(c, 4)));
    tx.setDiscountValue(BigDecimal.ZERO);
    tx.setLiquidValue(tx.getGrossValue());
    tx.setNsu(toLong(col(c, 5)));
    tx.setReferenceNumber(col(c, 6));
    tx.setSaleDate(toOffsetDateTime(parseDate(col(c, 3)), null));
    tx.setCapture(CaptureEnum.PDV.getCode());
    tx.setFlag(safeFlag(acquirer, col(c, 7)));
    tx.setAuthorization(col(c, 8));
    tx.setFirstInstallmentValue(tx.getLiquidValue());
    return tx;
  }

  private TransactionAcqEntity baseTransaction(List<String> c, int lineNumber, ProcessedFileEntity processedFile,
                                               Integer pvNumber, Integer rvNumber, AcquirerEntity acquirer,
                                               EstablishmentEntity establishment, SalesSummaryEntity summary) {
    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setRecordType(col(c, 0));
    tx.setLineNumber(lineNumber);
    tx.setEstablishment(establishment);
    tx.setCompany(establishment != null ? establishment.getCompany() : null);
    tx.setAcquirer(acquirer);
    tx.setProcessedFile(processedFile);
    tx.setSalesSummary(summary);
    tx.setRvNumber(rvNumber);
    tx.setInstallment(1);
    tx.setModality(ModalityEnum.CASH_DEBIT.getCode());
    tx.setStatusAudit(STATUS_PENDING);
    tx.setStatusPaymentBank(STATUS_PENDING);
    tx.setTransactionStatus(TRANSACTION_PENDING);
    tx.setTransactionStatusReason(0);
    tx.setTipValue(BigDecimal.ZERO);
    tx.setFlexRate(BigDecimal.ZERO);
    tx.setOtherInstallmentsValue(BigDecimal.ZERO);
    tx.setPurchaseValue(BigDecimal.ZERO);
    tx.setWithdrawalValue(BigDecimal.ZERO);
    return tx;
  }

  private void addTransactionWithInstallment(List<TransactionAcqEntity> transactions, List<InstallmentAcqEntity> installments, TransactionAcqEntity tx) {
    if (tx == null) return;
    transactions.add(tx);

    InstallmentAcqEntity installment = new InstallmentAcqEntity();
    installment.setTransaction(tx);
    installment.setInstallment(1);
    installment.setGrossValue(zero(tx.getGrossValue()));
    installment.setDiscountValue(zero(tx.getDiscountValue()));
    installment.setLiquidValue(zero(tx.getLiquidValue()));
    installment.setAdjustmentValue(BigDecimal.ZERO);
    installment.setStatusPaymentBank(STATUS_PENDING);
    installment.setInstallmentStatus(StatusInstallmentEnum.SCHEDULED.getCode());
    installment.setExpectedPaymentDate(tx.getCreditDate() != null ? tx.getCreditDate() : tx.getSalesSummary() != null ? tx.getSalesSummary().getFirstInstallmentCreditDate() : null);
    installments.add(installment);
  }

  private AcquirerEntity safeAcquirer() {
    try {
      return lookupService.acquirerByIdentifier("REDE");
    } catch (Exception ex) {
      log.debug("Adquirente Rede não encontrada durante parsing EEVD: {}", ex.getMessage());
      return null;
    }
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    if (pvNumber == null) return null;
    try {
      return lookupService.establishmentByPvNumber(pvNumber);
    } catch (Exception ex) {
      log.debug("Estabelecimento não encontrado para PV {} durante parsing EEVD: {}", pvNumber, ex.getMessage());
      return null;
    }
  }

  private BankingDomicileEntity safeBankingDomicile(Integer agency, Integer currentAccount, CompanyEntity company) {
    try {
      return bankingDomicileResolver.resolve(agency, currentAccount, company).orElse(null);
    } catch (Exception ex) {
      log.debug("Domicílio bancário não encontrado para agência={} conta={} durante parsing EEVD: {}", agency, currentAccount, ex.getMessage());
      return null;
    }
  }

  private FlagEntity safeFlag(AcquirerEntity acquirer, String code) {
    if (acquirer == null || code == null || code.isBlank()) return null;
    try {
      return lookupService.flagByAcquirerCode(acquirer, code.trim());
    } catch (Exception ex) {
      log.debug("Bandeira não encontrada para código Rede '{}' durante parsing EEVD: {}", code, ex.getMessage());
      return null;
    }
  }

  private SalesSummaryEntity findSummary(List<SalesSummaryEntity> summaries, Integer pvNumber, Integer rvNumber) {
    if (summaries.isEmpty()) return null;
    for (int i = summaries.size() - 1; i >= 0; i--) {
      SalesSummaryEntity summary = summaries.get(i);
      if (Objects.equals(summary.getPvNumber(), pvNumber) && Objects.equals(summary.getRvNumber(), rvNumber)) {
        return summary;
      }
    }
    return summaries.get(summaries.size() - 1);
  }

  private List<String> splitCsv(String line) {
    if (line == null) return List.of();
    return Arrays.stream(line.split(",", -1)).map(String::trim).toList();
  }

  private String col(List<String> columns, int index) {
    if (columns == null || index < 0 || index >= columns.size()) return null;
    String value = columns.get(index);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private LocalDate parseDate(String value) {
    if (value == null || value.isBlank() || value.matches("0+")) return null;
    try {
      return LocalDate.parse(value.trim(), DATE_FORMAT);
    } catch (Exception ex) {
      return null;
    }
  }

  private OffsetDateTime toOffsetDateTime(LocalDate date, String time) {
    if (date == null) return null;
    try {
      if (time == null || time.isBlank() || time.matches("0+")) {
        return date.atStartOfDay().atOffset(ZoneOffset.UTC);
      }
      return LocalDateTime.of(date, java.time.LocalTime.parse(time.trim(), TIME_FORMAT)).atOffset(ZoneOffset.UTC);
    } catch (Exception ex) {
      return date.atStartOfDay().atOffset(ZoneOffset.UTC);
    }
  }

  private BigDecimal money(String value) {
    if (value == null || value.isBlank() || value.matches("0+")) return BigDecimal.ZERO;
    String normalized = value.trim().replace(".", "").replace(",", ".");
    try {
      if (normalized.matches("-?\\d+")) return new BigDecimal(normalized).movePointLeft(2);
      return new BigDecimal(normalized);
    } catch (Exception ex) {
      return BigDecimal.ZERO;
    }
  }

  private Integer toInteger(String value) {
    if (value == null || value.isBlank()) return null;
    if (value.matches("0+")) return 0;
    try {
      return Integer.parseInt(value.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private Long toLong(String value) {
    if (value == null || value.isBlank() || value.matches("0+")) return null;
    try {
      return Long.parseLong(value.trim());
    } catch (Exception ex) {
      return null;
    }
  }

  private Integer resolveCapture(String code) {
    if (code == null || code.isBlank()) return CaptureEnum.NULL.getCode();
    return switch (code.trim()) {
      case "1" -> CaptureEnum.MANUAL.getCode();
      case "2", "3" -> CaptureEnum.PDV.getCode();
      case "5" -> CaptureEnum.ECOMMERCE.getCode();
      default -> CaptureEnum.NULL.getCode();
    };
  }

  private Integer resolveSummaryModality(String summaryType) {
    if (summaryType == null || summaryType.isBlank()) return ModalityEnum.CASH_DEBIT.getCode();
    return switch (summaryType.trim().toUpperCase(Locale.ROOT)) {
      case "P" -> ModalityEnum.OUTROS.getCode();
      default -> ModalityEnum.CASH_DEBIT.getCode();
    };
  }

  private BigDecimal calculateRate(BigDecimal grossValue, BigDecimal discountValue) {
    if (grossValue == null || grossValue.signum() == 0 || discountValue == null) return BigDecimal.ZERO;
    return discountValue.multiply(BigDecimal.valueOf(100)).divide(grossValue, 6, RoundingMode.HALF_UP);
  }

  private BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String defaultIfBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String safeMessage(Exception ex) {
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }
}
