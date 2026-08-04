package com.cardsync.core.file.service;

import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.core.file.util.CieloAdjustmentReasonCatalog;
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
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Lê o arquivo CIELO03 (Captura/Previsão de vendas) do Extrato Eletrônico Cielo v15.15.
 *
 * Fase 1: o Registro E (Detalhe do Lançamento) com Tipo de lançamento "01"/"02"/"03"
 * (venda débito/crédito/parcelada — 94,7% dos registros reais amostrados) é convertido em
 * {@link TransactionAcqEntity}. CIELO03 não tem Registro D (UR Agenda: "demonstrado apenas em
 * arquivos 04 e 09" — manual pág. 22), então não há agrupamento em {@link SalesSummaryEntity}
 * nesta fase.
 *
 * Fase 6: os tipos de lançamento de ajuste "04"/"05"/"08"/"10" (únicos com ocorrência real no
 * histórico completo do cliente — os demais códigos da Tabela II do manual não têm nenhuma
 * ocorrência real) são convertidos em {@link AdjustmentEntity} — ver buildAdjustment. Os demais
 * tipos de lançamento continuam apenas contabilizados como ignorados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessCielo03Service {

  private static final Charset CIELO_CHARSET = Charset.forName("windows-1252");
  private static final Set<String> SALE_LAUNCH_TYPES = Set.of("01", "02", "03");
  private static final Set<String> ADJUSTMENT_LAUNCH_TYPES = Set.of("04", "05", "08", "10");

  private final FileLookupService lookupService;
  private final MoveFileService moveFileService;
  private final TransactionAcqRepository transactionAcqRepository;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final ProcessedFileRepository processedFileRepository;
  private final AdjustmentRepository adjustmentRepository;
  private final AdjustmentTransactionLinkService adjustmentTransactionLinkService;

  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public void processFile(Path file, FileProcessingProperties.FilePaths paths, String contentHash) {
    ProcessedFileEntity processedFile = null;
    try {
      log.info("▶ Iniciando processamento Cielo CIELO03: {}", file.getFileName());
      List<String> lines = Files.readAllLines(file, CIELO_CHARSET);
      processedFile = new ProcessedFileEntity();
      processedFile.setContentHash(contentHash);

      List<TransactionAcqEntity> transactions = new ArrayList<>();
      List<InstallmentAcqEntity> installments = new ArrayList<>();
      List<SalesSummaryEntity> summaries = new ArrayList<>();
      List<AdjustmentEntity> adjustments = new ArrayList<>();
      Map<String, Integer> launchTypeCounts = new TreeMap<>();
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
          case "E" -> {
            recognized++;
            String launchType = trim(FileParserUtils.extractStringLine(line, "27-29", lineNumber));
            launchTypeCounts.merge(launchType == null ? "?" : launchType, 1, Integer::sum);
            if (ADJUSTMENT_LAUNCH_TYPES.contains(launchType)) {
              adjustments.add(buildAdjustment(line, lineNumber, processedFile, launchType));
              continue;
            }
            if (!SALE_LAUNCH_TYPES.contains(launchType)) {
              ignored++;
              warnings++;
              processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
                "CIELO03_UNSUPPORTED_LAUNCH_TYPE", "Tipo de lançamento Cielo ainda não suportado: " + launchType, line));
              continue;
            }
            TransactionAcqEntity tx = buildTransaction(line, lineNumber, processedFile, launchType);
            SalesSummaryEntity summary = buildSalesSummary(tx);
            tx.setSalesSummary(summary);
            transactions.add(tx);
            installments.add(buildInstallment(tx, line, lineNumber, launchType));
            summaries.add(summary);
          }
          default -> {
            ignored++;
            warnings++;
            processedFile.addError(ProcessedFileErrorEntity.of(lineNumber, ProcessedFileErrorTypeEnum.VALIDATION,
              "CIELO03_UNSUPPORTED_RECORD_TYPE", "Tipo de registro Cielo ainda não mapeado: " + recordType, line));
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
          + ", tiposLancamento=" + launchTypeCounts);

      collectPvNumbers(processedFile, transactions);

      processedFileRepository.save(processedFile);
      salesSummaryRepository.saveAll(summaries);
      transactionAcqRepository.saveAll(transactions);
      installmentAcqRepository.saveAll(installments);
      if (!adjustments.isEmpty()) {
        adjustmentRepository.saveAll(adjustments);
        adjustmentTransactionLinkService.linkSavedAdjustments(adjustments);
      }

      moveFileService.moveAfterCommit(file, paths.getProcessed(), processedFile.getDateFile());
      log.info("✅ CIELO03 {} finalizado: status={}, {}", file.getFileName(), processedFile.getStatus(), processedFile.getStatusMessage());
    } catch (DataIntegrityViolationException ex) {
      log.error("⚠ Arquivo CIELO03 {} já processado anteriormente.", file.getFileName());
      if (processedFile != null) processedFile.setStatus(FileStatusEnum.DUPLICATE);
      moveFileService.moveAfterRollback(file, paths.getDuplicate(), processedFile == null ? null : processedFile.getDateFile());
      throw ex;
    } catch (Exception ex) {
      log.error("❌ Erro ao processar CIELO03 {}: {}", file.getFileName(), safeMessage(ex), ex);
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
  TransactionAcqEntity buildTransaction(String line, int lineNumber, ProcessedFileEntity processedFile, String launchType) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "1-11", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String flagCode = trim(FileParserUtils.extractStringLine(line, "11-14", lineNumber));

    BigDecimal grossValue = FileParserUtils.extractSignedMoneyLine(line, "260-274", lineNumber);
    BigDecimal liquidValue = FileParserUtils.extractSignedMoneyLine(line, "274-288", lineNumber);
    BigDecimal discountValue = FileParserUtils.extractSignedMoneyLine(line, "288-302", lineNumber).abs();
    Integer totalInstallments = FileParserUtils.extractIntegerLine(line, "19-21", lineNumber);
    String chaveUR = trim(FileParserUtils.extractStringLine(line, "29-129", lineNumber));

    TransactionAcqEntity tx = new TransactionAcqEntity();
    tx.setLineNumber(lineNumber);
    tx.setRecordType(launchType);
    tx.setProcessedFile(processedFile);
    tx.setAcquirer(acquirer);
    tx.setEstablishment(establishment);
    tx.setCompany(establishment != null ? establishment.getCompany() : null);
    tx.setFlag(safeFlag(acquirer, flagCode));
    tx.setRvNumber(FileParserUtils.deriveConciliationKey(chaveUR));
    tx.setAuthorization(FileParserUtils.extractStringLine(line, "21-27", lineNumber));
    tx.setCardNumber(maskedCardNumber(line, lineNumber));
    tx.setNsu(FileParserUtils.extractLongLine(line, "175-181", lineNumber));
    tx.setTid(FileParserUtils.extractStringLine(line, "191-211", lineNumber));
    tx.setReferenceNumber(FileParserUtils.extractStringLine(line, "211-231", lineNumber));
    tx.setGrossValue(grossValue);
    tx.setLiquidValue(liquidValue);
    tx.setDiscountValue(discountValue);
    tx.setMdrRate(calculateRate(grossValue, discountValue));
    tx.setMachine(FileParserUtils.extractStringLine(line, "543-551", lineNumber));
    tx.setSaleDate(FileParserUtils.extractOffsetDateTimeLine(line, lineNumber, "565-573", "470-476"));
    tx.setInstallment(resolveTotalInstallments(launchType, totalInstallments));
    tx.setModality(resolveModality(launchType, totalInstallments));
    tx.setCapture(resolveCapture(trim(FileParserUtils.extractStringLine(line, "540-543", lineNumber))));
    tx.setFirstInstallmentValue(BigDecimal.ZERO);
    tx.setOtherInstallmentsValue(BigDecimal.ZERO);
    tx.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    tx.setStatusTransaction(StatusTransactionEnum.PENDING);
    tx.setStatusTransactionReason(StatusTransactionReasonEnum.NULL);
    return tx;
  }

  /**
   * Uma parcela por transação, espelhando ProcessRedeEeVdService.addTransactionWithInstallment —
   * é o que permite BankReconciliationService.propagateCreditOrdersToInstallments casar, por valor
   * (acquirer + rvNumber + installment), um CreditOrderEntity (criado pelo CIELO04) com esta venda.
   * expectedPaymentDate fica null: a Cielo não informa a data prevista de pagamento na captura
   * (isso só aparece no Registro D do CIELO04, na liquidação).
   *
   * O installment aqui é a parcela ATUAL (campo "Parcela" da linha), deliberadamente diferente de
   * {@code tx.getInstallment()} — este último guarda o TOTAL de parcelas da venda, na mesma
   * convenção do Rede (ProcessRedeEeVcService: installment do TransactionAcqEntity = total, só o
   * InstallmentAcqEntity é por parcela), o que ContractedAcquirerRateLookupService/tela de vendas
   * ACQ esperam pra bater com a bandeira/modalidade certa. Recalculado a partir da linha em vez de
   * derivado de tx, pra não reintroduzir o acoplamento que causava essa confusão.
   * Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring.
   */
  InstallmentAcqEntity buildInstallment(TransactionAcqEntity tx, String line, int lineNumber, String launchType) {
    Integer parcela = FileParserUtils.extractIntegerLine(line, "17-19", lineNumber);

    InstallmentAcqEntity installment = new InstallmentAcqEntity();
    installment.setTransaction(tx);
    installment.setInstallment(resolveCurrentInstallment(launchType, parcela));
    installment.setGrossValue(zero(tx.getGrossValue()));
    installment.setDiscountValue(zero(tx.getDiscountValue()));
    installment.setLiquidValue(zero(tx.getLiquidValue()));
    installment.setAdjustmentValue(BigDecimal.ZERO);
    installment.setStatusPaymentBank(StatusPaymentBankEnum.PENDING.getCode());
    installment.setInstallmentStatus(StatusInstallmentEnum.SCHEDULED.getCode());
    return installment;
  }

  /**
   * Um resumo por transação — a Cielo não tem uma linha de "resumo" separada como o Rede (Registro
   * "006"/"010" da EEVC); cada Registro E de venda já é seu próprio resumo. Necessário pra
   * ProcessCielo04Service.safeSalesSummary conseguir achar e vincular, e a Etapa 6 da esteira
   * financeira (SalesSummaryCreditOrderReconciliationService/CreditOrderOrphanLinkingService)
   * marcar salesSummaryStatus como RECONCILED — sem isso a ordem de crédito do CIELO04 nunca fica
   * elegível pra conciliação bancária (BankReconciliationService só considera
   * salesSummaryStatus=RECONCILED).
   * Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring.
   */
  SalesSummaryEntity buildSalesSummary(TransactionAcqEntity tx) {
    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setRecordType(tx.getRecordType());
    summary.setLineNumber(tx.getLineNumber());
    summary.setPvNumber(tx.getEstablishment() != null ? tx.getEstablishment().getPvNumber() : null);
    summary.setRvNumber(tx.getRvNumber());
    summary.setGrossValue(zero(tx.getGrossValue()));
    summary.setDiscountValue(zero(tx.getDiscountValue()));
    summary.setLiquidValue(zero(tx.getLiquidValue()));
    summary.setTipValue(BigDecimal.ZERO);
    summary.setRejectedValue(BigDecimal.ZERO);
    summary.setAdjustedValue(BigDecimal.ZERO);
    summary.setManualGenerated(false);
    summary.setRvDate(tx.getSaleDate() != null ? tx.getSaleDate().toLocalDate() : null);
    summary.setModality(tx.getModality());
    summary.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    summary.setCreditOrderStatus(StatusReconciliationEnum.PENDING);
    summary.setTransactionsStatus(StatusReconciliationEnum.PENDING);
    summary.setAcquirer(tx.getAcquirer());
    summary.setCompany(tx.getCompany());
    summary.setFlag(tx.getFlag());
    summary.setProcessedFile(tx.getProcessedFile());
    return summary;
  }

  /**
   * Tipos de lançamento "04" (Ajuste a débito), "05" (Ajuste a crédito), "08" (Contestação do
   * portador do cartão — chargeback Cielo) e "10" (Aluguel de máquina) — únicos com ocorrência
   * real no histórico completo do cliente (108 linhas/ano, ver Fase 6 do plano). O Registro E tem
   * o MESMO layout de posições pra linha de venda e de ajuste, só o conteúdo muda.
   *
   * cancellationValueRequested só é setado pra "04"/"08" com NSU presente — é o que faz
   * AcquirerSaleCancellationService considerar esse ajuste candidato a cancelamento (só um débito
   * real contra uma venda identificável entra nessa fila; "05" é crédito ao estabelecimento, "10"
   * (aluguel de máquina) não tem NSU/venda associada, mesmo padrão do "011" da Rede/EEVD).
   * Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring.
   */
  AdjustmentEntity buildAdjustment(String line, int lineNumber, ProcessedFileEntity processedFile, String launchType) {
    Integer pvNumber = FileParserUtils.extractIntegerLine(line, "1-11", lineNumber);
    AcquirerEntity acquirer = safeAcquirer();
    EstablishmentEntity establishment = safeEstablishment(pvNumber);
    String chaveUR = trim(FileParserUtils.extractStringLine(line, "29-129", lineNumber));
    Long nsu = FileParserUtils.extractLongLine(line, "175-181", lineNumber);
    BigDecimal adjustmentValue = FileParserUtils.extractSignedMoneyLine(line, "260-274", lineNumber);
    String rawAdjustmentCode = trim(FileParserUtils.extractStringLine(line, "151-155", lineNumber));
    boolean hasNsu = nsu != null && nsu != 0L;

    AdjustmentEntity adjustment = new AdjustmentEntity();
    adjustment.setLineNumber(lineNumber);
    adjustment.setRecordType(launchType);
    adjustment.setSourceRecordIdentifier(launchType);
    adjustment.setProcessedFile(processedFile);
    adjustment.setAdjustmentStatus(AdjustmentStatusEnum.PENDING);
    adjustment.setAcquirer(acquirer);
    adjustment.setEstablishment(establishment);
    adjustment.setCompany(establishment != null ? establishment.getCompany() : null);
    adjustment.setPvNumber(pvNumber);
    adjustment.setPvNumberOriginal(pvNumber);
    adjustment.setRvNumberOriginal(FileParserUtils.deriveConciliationKey(chaveUR));
    adjustment.setNsu(nsu);
    adjustment.setAuthorization(nonZeroAuthorization(FileParserUtils.extractStringLine(line, "21-27", lineNumber)));
    // Registro E só tem um campo de data relevante nessa posição — usado tanto como
    // "data da transação original" quanto "data do ajuste" (não há dois campos distintos aqui).
    LocalDate lineDate = FileParserUtils.extractDateLine(line, "565-573", lineNumber);
    adjustment.setTransactionDate(lineDate);
    adjustment.setAdjustmentDate(lineDate);
    adjustment.setAdjustmentValue(adjustmentValue);
    adjustment.setRawAdjustmentCode(rawAdjustmentCode);
    adjustment.setAdjustmentDescription(resolveAdjustmentDescription(launchType, rawAdjustmentCode));
    adjustment.setAdjustmentType(resolveAdjustmentType(launchType));

    if (hasNsu && ("04".equals(launchType) || "08".equals(launchType))) {
      BigDecimal absValue = adjustmentValue.abs();
      adjustment.setCancellationValueRequested(absValue);
      adjustment.setTransactionValue(absValue);
    }

    return adjustment;
  }

  private String resolveAdjustmentDescription(String launchType, String rawAdjustmentCode) {
    String fromCatalog = CieloAdjustmentReasonCatalog.get(rawAdjustmentCode);
    if (fromCatalog != null) {
      return fromCatalog;
    }
    return switch (launchType) {
      case "04" -> "Ajuste a débito";
      case "05" -> "Ajuste a crédito";
      case "08" -> "Contestação do portador do cartão";
      case "10" -> "Aluguel de máquina";
      default -> null;
    };
  }

  private String resolveAdjustmentType(String launchType) {
    return switch (launchType) {
      case "04" -> "CIELO_DEBIT_ADJUSTMENT";
      case "05" -> "CIELO_CREDIT_ADJUSTMENT";
      case "08" -> "CIELO_CHARGEBACK";
      case "10" -> "CIELO_MACHINE_RENTAL";
      default -> "CIELO_ADJUSTMENT";
    };
  }

  /**
   * "000000" é o preenchimento padrão do campo Autorização quando não se aplica (ex.: ajustes não
   * ligados a uma autorização de cartão específica) — mesmo padrão zero-vira-ausente já usado pro
   * NSU (ver hasNsu em buildAdjustment). Sem isso, AdjustmentTransactionLinkService trataria
   * "000000" como uma autorização real e nunca acharia (nem cairia pro fallback só-por-NSU).
   */
  private String nonZeroAuthorization(String authorization) {
    String trimmed = trim(authorization);
    if (trimmed == null || trimmed.isBlank() || trimmed.chars().allMatch(c -> c == '0')) {
      return null;
    }
    return trimmed;
  }

  private BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private String maskedCardNumber(String line, int lineNumber) {
    String bin = FileParserUtils.extractStringLine(line, "165-171", lineNumber);
    String lastFour = FileParserUtils.extractStringLine(line, "171-175", lineNumber);
    if ((bin == null || bin.isBlank()) && (lastFour == null || lastFour.isBlank())) return null;
    return (bin == null ? "" : bin) + "******" + (lastFour == null ? "" : lastFour);
  }

  /**
   * "Canal da venda" (Tabela VII do manual) → CaptureEnum. Só os códigos com equivalente claro são
   * mapeados (mesmo padrão conservador de ProcessRedeEeVdService.resolveCapture) — o resto (ex.:
   * "998" Não se aplica, EDI/GDS/central de atendimento) fica null em vez de forçar um palpite.
   * Amostragem de dados reais (~2000 linhas) só encontrou "007" (E-commerce), "008" (TEF/PDV) e
   * "998" (Não se aplica) — os demais códigos da tabela são mapeados por completude/robustez.
   */
  private Integer resolveCapture(String channelCode) {
    if (channelCode == null || channelCode.isBlank()) return CaptureEnum.NULL.getCode();
    return switch (channelCode) {
      case "000", "001" -> CaptureEnum.POS.getCode();
      case "008" -> CaptureEnum.PDV.getCode();
      case "003", "004", "010", "011", "015" -> CaptureEnum.MANUAL.getCode();
      case "005", "006", "007" -> CaptureEnum.ECOMMERCE.getCode();
      default -> CaptureEnum.NULL.getCode();
    };
  }

  /** Número da parcela ATUAL (campo "Parcela") — usado só no InstallmentAcqEntity (ver buildInstallment), pra casar por valor com o installmentNumber que o CIELO04 vai reportar quando essa parcela específica for paga. */
  private Integer resolveCurrentInstallment(String launchType, Integer parcela) {
    if (!"03".equals(launchType)) return 1;
    return parcela == null || parcela <= 0 ? 1 : parcela;
  }

  /** Número TOTAL de parcelas (campo "Número total de parcelas") — vai em tx.installment (convenção do Rede: TransactionAcqEntity.installment = total) e escalona a modalidade. */
  private Integer resolveTotalInstallments(String launchType, Integer totalInstallments) {
    if (!"03".equals(launchType)) return 1;
    return totalInstallments == null || totalInstallments <= 0 ? 1 : totalInstallments;
  }

  /** Número TOTAL de parcelas (campo "Número total de parcelas") — só usado pra escalonar a modalidade. */
  private Integer resolveModality(String launchType, Integer totalInstallments) {
    if ("01".equals(launchType)) return ModalityEnum.CASH_DEBIT.getCode();
    if (!"03".equals(launchType)) return ModalityEnum.CASH_CREDIT.getCode();

    int total = totalInstallments == null || totalInstallments <= 0 ? 1 : totalInstallments;
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
      log.debug("Adquirente Cielo não encontrada durante parsing CIELO03: {}", ex.getMessage());
      return null;
    }
  }

  private EstablishmentEntity safeEstablishment(Integer pvNumber) {
    if (pvNumber == null) return null;
    try {
      return lookupService.establishmentByPvNumber(pvNumber);
    } catch (Exception ex) {
      log.debug("Estabelecimento não encontrado para PV {} durante parsing CIELO03: {}", pvNumber, ex.getMessage());
      return null;
    }
  }

  private FlagEntity safeFlag(AcquirerEntity acquirer, String code) {
    if (acquirer == null || code == null || code.isBlank()) return null;
    try {
      return lookupService.flagByAcquirerCode(acquirer, code);
    } catch (Exception ex) {
      log.debug("Bandeira não encontrada para código Cielo '{}' durante parsing CIELO03: {}", code, ex.getMessage());
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
