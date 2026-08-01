package com.cardsync.core.reconciliation.summary;

import com.cardsync.bff.controller.v1.mapper.model.SaleSummaryModelAssembler;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderImportPreviewResult;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderImportResult;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderImportSkipReason;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualInput;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderManualResult;
import com.cardsync.bff.controller.v1.representation.input.CreditOrderSkipReason;
import com.cardsync.bff.controller.v1.representation.model.transactions.SaleSummaryModel;
import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.file.acquirerreport.dto.AcquirerPaymentReportCsvReader;
import com.cardsync.core.file.acquirerreport.dto.AcquirerPaymentReportRow;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.core.reconciliation.BankReconciliationService;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.HolidayRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.infrastructure.repository.spec.SaleSummarySpecs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditOrderManualService {

  private static final int RECONCILIATION_STATUS_PENDING = 1;

  private final SaleSummarySpecs saleSummarySpecs;
  private final AdjustmentRepository adjustmentRepository;
  private final CreditOrderRepository creditOrderRepository;
  private final SalesSummaryRepository salesSummaryRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final SaleSummaryModelAssembler saleSummaryModelAssembler;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final HolidayRepository holidayRepository;
  private final AcquirerPaymentReportCsvReader acquirerPaymentReportCsvReader;

  @Transactional(readOnly = true)
  public Page<SaleSummaryModel> searchPendingSummaries(Pageable pageable, ListQueryDto<SaleSummaryFilter> query) {
    int days = reconciliationSettingsService.getCreditOrderPendingDays();
    LocalDate cutoffDate = LocalDate.now().minusDays(days);
    LocalDate yesterday  = LocalDate.now().minusDays(1);

    Specification<SalesSummaryEntity> filterSpec = saleSummarySpecs.fromQueryForPendingCreditOrdersTotals(query, cutoffDate, yesterday);
    Specification<SalesSummaryEntity> dataSpec   = saleSummarySpecs.fromQueryForPendingCreditOrders(query, cutoffDate, yesterday);

    long total = salesSummaryRepository.count(filterSpec);
    // dataSpec já monta o ORDER BY completo (orderByTableSort/tableSort, com os aliases de
    // nextInstallmentValue/nextInstallmentDate etc.) — SimpleJpaRepository#findAll(Specification,
    // Pageable) reaplica pageable.getSort() por cima, usando os nomes de campo crus direto contra
    // SalesSummaryEntity (sem conhecer os aliases), o que quebra com "No property 'X' found for
    // type 'SalesSummaryEntity'" pra qualquer campo que só existe no DTO/no map de aliases (ex.:
    // nextInstallmentValue/nextInstallmentDate). Por isso passamos aqui um Pageable SEM sort —
    // a ordenação real já vem da Specification; o pageable original (com sort) continua sendo
    // usado só pro metadado da resposta (PageImpl abaixo).
    Pageable pageableWithoutSort = pageable.isPaged()
      ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
      : Pageable.unpaged();
    List<SalesSummaryEntity> entities = total == 0
      ? List.of()
      : salesSummaryRepository.findAll(dataSpec, pageableWithoutSort).getContent();
    List<SaleSummaryModel> content = entities.stream().map(saleSummaryModelAssembler::toModel).toList();

    fillNextInstallmentPreview(entities, content);

    return new PageImpl<>(content, pageable, total);
  }

  /**
   * Prévia (sem gravar nada) do valor e da data de vencimento que a próxima ordem de crédito
   * teria se {@link #create} fosse chamado agora para cada resumo da página — mesma fórmula de
   * {@link #buildCreditOrder}, buscando installmentTotal, as parcelas já existentes e os ajustes
   * de débito em lote (uma consulta pra página inteira, não uma por linha).
   */
  private void fillNextInstallmentPreview(List<SalesSummaryEntity> entities, List<SaleSummaryModel> content) {
    if (entities.isEmpty()) return;

    List<UUID> summaryIds = entities.stream().map(SalesSummaryEntity::getId).toList();

    Map<UUID, Integer> installmentTotalsBySummaryId = new HashMap<>();
    for (Object[] row : transactionAcqRepository.findMaxInstallmentBySalesSummaryIdIn(summaryIds)) {
      installmentTotalsBySummaryId.put((UUID) row[0], ((Number) row[1]).intValue());
    }

    Map<UUID, Set<Integer>> existingInstallmentsBySummaryId = new HashMap<>();
    for (Object[] row : creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(summaryIds)) {
      existingInstallmentsBySummaryId
        .computeIfAbsent((UUID) row[0], ignored -> new HashSet<>())
        .add((Integer) row[1]);
    }

    Map<UUID, BigDecimal> debitAdjustmentsBySummaryId = new HashMap<>();
    for (Object[] row : adjustmentRepository.sumDebitAdjustmentsBySalesSummaryIdIn(summaryIds)) {
      debitAdjustmentsBySummaryId.put((UUID) row[0], (BigDecimal) row[1]);
    }

    Map<UUID, SalesSummaryEntity> entitiesById = entities.stream()
      .collect(Collectors.toMap(SalesSummaryEntity::getId, e -> e));

    for (SaleSummaryModel model : content) {
      SalesSummaryEntity summary = entitiesById.get(model.getId());
      int installmentTotal = installmentTotalsBySummaryId.getOrDefault(model.getId(), 1);
      Set<Integer> existing = existingInstallmentsBySummaryId.getOrDefault(model.getId(), Set.of());
      BigDecimal debitAdjustments = debitAdjustmentsBySummaryId.getOrDefault(model.getId(), BigDecimal.ZERO);
      BigDecimal netLiquidValue = orZero(model.getLiquidValue()).subtract(debitAdjustments);

      model.setNextInstallmentValue(computeInstallmentValue(netLiquidValue, installmentTotal));

      int nextInstallmentNumber = firstMissingInstallmentNumber(existing, installmentTotal);
      LocalDate baseDate = summary != null
        ? (summary.getFirstInstallmentCreditDate() != null ? summary.getFirstInstallmentCreditDate() : summary.getRvDate())
        : null;
      model.setNextInstallmentDate(baseDate != null
        ? adjustToPreviousBusinessDay(baseDate.plusMonths(nextInstallmentNumber - 1))
        : null);
    }
  }

  /** Menor número de parcela em [1, installmentTotal] ainda sem ordem de crédito. */
  private static int firstMissingInstallmentNumber(Set<Integer> existingInstallments, int installmentTotal) {
    for (int i = 1; i <= installmentTotal; i++) {
      if (!existingInstallments.contains(i)) {
        return i;
      }
    }
    return installmentTotal;
  }

  @Transactional
  public CreditOrderManualResult create(CreditOrderManualInput input) {
    List<UUID> createdIds = new ArrayList<>();
    List<CreditOrderSkipReason> skippedReasons = new ArrayList<>();

    for (UUID summaryId : input.summaryIds()) {
      SalesSummaryEntity summary = salesSummaryRepository.findById(summaryId).orElse(null);
      if (summary == null) {
        skippedReasons.add(new CreditOrderSkipReason(null, "SUMMARY_NOT_FOUND", 0));
        log.warn("⚠️ Resumo não encontrado: {}", summaryId);
        continue;
      }

      try {
        int installmentTotal = transactionAcqRepository.findMaxInstallmentBySalesSummaryId(summaryId);
        Set<Integer> existing = creditOrderRepository.findInstallmentNumbersBySalesSummaryId(summaryId);
        BigDecimal debitAdjustments = adjustmentRepository.sumDebitAdjustmentsBySalesSummaryId(summaryId);

        List<Integer> missingInstallments = new ArrayList<>();
        for (int i = 1; i <= installmentTotal; i++) {
          if (!existing.contains(i)) {
            missingInstallments.add(i);
          }
        }

        if (missingInstallments.isEmpty()) {
          skippedReasons.add(new CreditOrderSkipReason(String.valueOf(summary.getRvNumber()), "ALL_INSTALLMENTS_COVERED", installmentTotal));
          continue;
        }

        LocalDate baseDate = summary.getFirstInstallmentCreditDate() != null
          ? summary.getFirstInstallmentCreditDate()
          : summary.getRvDate();

        // Fecha TODAS as lacunas do resumo nesta chamada, não só a primeira parcela faltante —
        // antes, um resumo com múltiplas parcelas ausentes exigia uma chamada manual por parcela.
        int createdForThisSummary = 0;
        for (int installmentNumber : missingInstallments) {
          LocalDate nextReleaseDate = baseDate != null ? baseDate.plusMonths(installmentNumber - 1) : null;
          if (nextReleaseDate != null && nextReleaseDate.isAfter(LocalDate.now().minusDays(1))) {
            skippedReasons.add(new CreditOrderSkipReason(String.valueOf(summary.getRvNumber()), "FUTURE_RELEASE_DATE", installmentNumber));
            log.info("⏭️ Parcela {}/{} ignorada — vencimento futuro: {}", installmentNumber, installmentTotal, nextReleaseDate);
            // Datas crescem com o número da parcela — as seguintes também seriam futuras.
            break;
          }

          CreditOrderEntity co = buildCreditOrder(summary, installmentNumber, installmentTotal, debitAdjustments);
          co = creditOrderRepository.save(co);
          createdIds.add(co.getId());
          createdForThisSummary++;

          log.info("✅ Ordem de crédito manual criada: id={}, summaryId={}, parcela={}/{}, releaseDate={}, releaseValue={}",
            co.getId(), summaryId, installmentNumber, installmentTotal, co.getReleaseDate(), co.getReleaseValue());
        }

        if (createdForThisSummary > 0) {
          updateSummaryCreditOrderStatus(summary);
        }

      } catch (IllegalStateException e) {
        skippedReasons.add(new CreditOrderSkipReason(String.valueOf(summary.getRvNumber()), "UNEXPECTED_ERROR", 0));
        log.warn("⚠️ Falha ao criar ordem de crédito para summary {}: {}", summaryId, e.getMessage());
      }
    }

    return new CreditOrderManualResult(createdIds.size(), skippedReasons.size(), createdIds, skippedReasons);
  }

  /**
   * Importação em lote a partir do relatório real de pagamentos da adquirente (CSV), em vez da
   * fórmula de aproximação de {@link #buildCreditOrder}. Segue a MESMA regra de elegibilidade já
   * usada pela tela/criação manual (só gera ordem para parcela ainda ausente — nunca sobrescreve
   * uma ordem já existente, gerada manualmente ou por este próprio import); quando a parcela do
   * arquivo já tem ordem, a linha é apenas ignorada e reportada.
   */
  @Transactional(readOnly = true)
  public CreditOrderImportPreviewResult previewAcquirerReportImport(MultipartFile[] files) {
    ImportProcessingResult processed = processAcquirerReport(files, false);
    return new CreditOrderImportPreviewResult(
      fileNames(files),
      processed.analyzedLines(),
      processed.eligibleCount(),
      processed.totalValue(),
      processed.skippedReasons().size(),
      processed.skippedReasons()
    );
  }

  @Transactional
  public CreditOrderImportResult importFromAcquirerReport(MultipartFile[] files) {
    ImportProcessingResult processed = processAcquirerReport(files, true);
    return new CreditOrderImportResult(
      processed.analyzedLines(),
      processed.createdIds().size(),
      processed.skippedReasons().size(),
      processed.createdIds(),
      processed.skippedReasons()
    );
  }

  private static List<String> fileNames(MultipartFile[] files) {
    return java.util.Arrays.stream(files).map(MultipartFile::getOriginalFilename).toList();
  }

  private record ImportProcessingResult(
    int analyzedLines,
    List<UUID> createdIds,
    List<CreditOrderImportSkipReason> skippedReasons,
    int eligibleCount,
    BigDecimal totalValue
  ) {}

  /**
   * Lógica compartilhada entre a prévia ({@link #previewAcquirerReportImport}, só leitura, usada
   * pela tela de confirmação antes do usuário confirmar o import) e o import de fato
   * ({@link #importFromAcquirerReport}) — {@code persist=false} roda a MESMA análise de
   * elegibilidade (RV encontrado, RV ambíguo, parcela já existente, erro de parsing) sem gravar
   * nada no banco, garantindo que a prévia mostrada ao usuário reflita exatamente o que a
   * confirmação vai gerar.
   */
  private ImportProcessingResult processAcquirerReport(MultipartFile[] files, boolean persist) {
    if (files == null || files.length == 0) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "Nenhum arquivo enviado.");
    }

    List<AcquirerPaymentReportRow> fileRows = new ArrayList<>();
    for (MultipartFile file : files) {
      try {
        fileRows.addAll(acquirerPaymentReportCsvReader.read(file));
      } catch (IOException e) {
        throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR,
          "Falha ao ler o arquivo " + file.getOriginalFilename() + ": " + e.getMessage());
      }
    }

    // O relatório real pode trazer mais de uma linha para o mesmo RV+parcela (ex.: parte da
    // parcela antecipada e o restante liquidado à parte, ambos no mesmo lote/data, possivelmente
    // em arquivos diferentes quando vários são importados juntos) — confirmado com o usuário
    // após uma conciliação bancária não fechar: RV 54949685 parcela 6 e RV 64749688 parcela 4
    // vieram cada um em duas linhas, e a primeira versão desta importação processava só a
    // primeira e descartava a segunda como "já tem ordem", subestimando o valor real da parcela.
    // Soma os valores de todas as linhas do mesmo RV+parcela (de todos os arquivos do lote) antes
    // de aplicar a regra de elegibilidade (ver mergeDuplicateInstallmentLines).
    List<AcquirerPaymentReportRow> rows = mergeDuplicateInstallmentLines(fileRows);

    Set<Integer> rvNumbers = rows.stream()
      .map(AcquirerPaymentReportRow::rvNumber)
      .collect(Collectors.toSet());
    Map<Integer, List<SalesSummaryEntity>> summariesByRvNumber = salesSummaryRepository.findByRvNumberIn(rvNumbers).stream()
      .collect(Collectors.groupingBy(SalesSummaryEntity::getRvNumber));

    List<UUID> summaryIds = summariesByRvNumber.values().stream()
      .flatMap(List::stream)
      .map(SalesSummaryEntity::getId)
      .toList();
    Map<UUID, Set<Integer>> existingInstallmentsBySummaryId = new HashMap<>();
    for (Object[] row : creditOrderRepository.findInstallmentNumbersBySalesSummaryIdIn(summaryIds)) {
      existingInstallmentsBySummaryId
        .computeIfAbsent((UUID) row[0], ignored -> new HashSet<>())
        .add((Integer) row[1]);
    }

    List<UUID> createdIds = new ArrayList<>();
    List<CreditOrderImportSkipReason> skippedReasons = new ArrayList<>();
    Set<SalesSummaryEntity> affectedSummaries = new LinkedHashSet<>();
    Map<UUID, Integer> installmentTotalBySummaryId = new HashMap<>();
    int eligibleCount = 0;
    BigDecimal totalValue = BigDecimal.ZERO;

    for (AcquirerPaymentReportRow row : rows) {
      String rvNumberText = row.rvNumber() != null ? String.valueOf(row.rvNumber()) : null;

      if (row.installmentNumber() == null || row.installmentTotal() == null
          || row.releaseDate() == null || row.releaseValue() == null) {
        skippedReasons.add(new CreditOrderImportSkipReason(row.fileName(), row.lineNumber(), rvNumberText, row.installmentNumber(), "PARSE_ERROR"));
        continue;
      }

      List<SalesSummaryEntity> candidates = summariesByRvNumber.getOrDefault(row.rvNumber(), List.of());
      SalesSummaryEntity summary;
      if (candidates.isEmpty()) {
        skippedReasons.add(new CreditOrderImportSkipReason(row.fileName(), row.lineNumber(), rvNumberText, row.installmentNumber(), "SUMMARY_NOT_FOUND"));
        continue;
      } else if (candidates.size() == 1) {
        summary = candidates.get(0);
      } else {
        List<SalesSummaryEntity> byPvNumber = row.pvNumber() != null
          ? candidates.stream().filter(c -> row.pvNumber().equals(c.getPvNumber())).toList()
          : List.of();
        if (byPvNumber.size() != 1) {
          skippedReasons.add(new CreditOrderImportSkipReason(row.fileName(), row.lineNumber(), rvNumberText, row.installmentNumber(), "AMBIGUOUS_RV"));
          continue;
        }
        summary = byPvNumber.get(0);
      }

      Set<Integer> existing = existingInstallmentsBySummaryId.getOrDefault(summary.getId(), Set.of());
      if (existing.contains(row.installmentNumber())) {
        skippedReasons.add(new CreditOrderImportSkipReason(row.fileName(), row.lineNumber(), rvNumberText, row.installmentNumber(), "ALREADY_HAS_CREDIT_ORDER"));
        continue;
      }

      eligibleCount++;
      totalValue = totalValue.add(row.releaseValue());

      if (persist) {
        CreditOrderEntity co = buildCreditOrderFromImportRow(summary, row);
        co = creditOrderRepository.save(co);
        createdIds.add(co.getId());

        log.info("✅ Ordem de crédito importada do relatório da adquirente: id={}, rv={}, parcela={}/{}, releaseDate={}, releaseValue={}",
          co.getId(), row.rvNumber(), row.installmentNumber(), row.installmentTotal(), co.getReleaseDate(), co.getReleaseValue());
      }

      existingInstallmentsBySummaryId.computeIfAbsent(summary.getId(), ignored -> new HashSet<>()).add(row.installmentNumber());
      installmentTotalBySummaryId.put(summary.getId(), row.installmentTotal());
      affectedSummaries.add(summary);
    }

    if (persist) {
      for (SalesSummaryEntity summary : affectedSummaries) {
        updateSummaryCreditOrderStatus(summary);
      }
    }

    return new ImportProcessingResult(fileRows.size(), createdIds, skippedReasons, eligibleCount, totalValue);
  }

  /**
   * Agrupa por RV+parcela e soma os valores de linhas repetidas (ver comentário em
   * {@link #importFromAcquirerReport}). Linhas sem RV ou parcela válidos (erro de parsing) não
   * têm chave de agrupamento e passam adiante sem alteração — continuam caindo em PARSE_ERROR.
   */
  private List<AcquirerPaymentReportRow> mergeDuplicateInstallmentLines(List<AcquirerPaymentReportRow> rows) {
    Map<String, AcquirerPaymentReportRow> merged = new java.util.LinkedHashMap<>();
    for (AcquirerPaymentReportRow row : rows) {
      if (row.rvNumber() == null || row.installmentNumber() == null) {
        merged.put("semChave#" + row.fileName() + "#" + row.lineNumber(), row);
        continue;
      }
      String key = row.rvNumber() + "/" + row.installmentNumber();
      AcquirerPaymentReportRow existing = merged.get(key);
      merged.put(key, existing == null ? row : sumDuplicateInstallmentLines(existing, row));
    }
    return new ArrayList<>(merged.values());
  }

  private AcquirerPaymentReportRow sumDuplicateInstallmentLines(AcquirerPaymentReportRow a, AcquirerPaymentReportRow b) {
    log.info("🔗 Combinando duas linhas do relatório para a mesma parcela: rv={}, parcela={}, arquivo/linha={}/{}+{}/{}",
      a.rvNumber(), a.installmentNumber(), a.fileName(), a.lineNumber(), b.fileName(), b.lineNumber());
    return new AcquirerPaymentReportRow(
      a.fileName(),
      a.lineNumber(),
      a.rvNumber(),
      a.pvNumber() != null ? a.pvNumber() : b.pvNumber(),
      a.installmentNumber(),
      a.installmentTotal() != null ? a.installmentTotal() : b.installmentTotal(),
      a.releaseDate() != null ? a.releaseDate() : b.releaseDate(),
      a.originalDueDate() != null ? a.originalDueDate() : b.originalDueDate(),
      sumNullable(a.releaseValue(), b.releaseValue()),
      sumNullable(a.grossValue(), b.grossValue()),
      sumNullable(a.discountValue(), b.discountValue()),
      a.status() != null ? a.status() : b.status()
    );
  }

  private static BigDecimal sumNullable(BigDecimal a, BigDecimal b) {
    if (a == null && b == null) return null;
    return (a != null ? a : BigDecimal.ZERO).add(b != null ? b : BigDecimal.ZERO);
  }

  private CreditOrderEntity buildCreditOrderFromImportRow(SalesSummaryEntity summary, AcquirerPaymentReportRow row) {
    BigDecimal grossPer = row.grossValue() != null
      ? row.grossValue()
      : computeInstallmentValue(summary.getGrossValue(), row.installmentTotal());
    BigDecimal discountPer = row.discountValue() != null
      ? row.discountValue()
      : computeInstallmentValue(summary.getDiscountValue(), row.installmentTotal());

    LocalDate baseDate = summary.getFirstInstallmentCreditDate() != null
      ? summary.getFirstInstallmentCreditDate()
      : summary.getRvDate();

    CreditOrderEntity co = new CreditOrderEntity();
    co.setPvCentralizer(summary.getPvNumber());
    co.setOriginalPvNumber(summary.getPvNumber());
    co.setRvNumber(summary.getRvNumber());
    co.setRvDate(summary.getRvDate());
    co.setSalesSummary(summary);
    co.setAcquirer(summary.getAcquirer());
    co.setCompany(summary.getCompany());
    co.setFlag(summary.getFlag());
    co.setBankingDomicile(summary.getBankingDomicile());
    co.setInstallmentNumber(row.installmentNumber());
    co.setInstallmentTotal(row.installmentTotal());
    co.setGrossRvValue(grossPer);
    co.setDiscountRateValue(discountPer);
    co.setReleaseValue(row.releaseValue());
    co.setReleaseDate(row.releaseDate());
    co.setCreditOrderDate(baseDate);
    co.setRecordType("MANUAL_GENERATED");
    co.setLaunchType("MANUAL_IMPORT");
    co.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    co.setSalesSummaryStatus(StatusReconciliationEnum.PENDING);
    co.setReconciliationStatus(RECONCILIATION_STATUS_PENDING);
    return co;
  }

  private CreditOrderEntity buildCreditOrder(
    SalesSummaryEntity summary, int installmentNumber, int installmentTotal, BigDecimal debitAdjustments
  ) {
    BigDecimal grossPer = computeInstallmentValue(summary.getGrossValue(), installmentTotal);
    BigDecimal discountPer = computeInstallmentValue(summary.getDiscountValue(), installmentTotal);
    // Desconta ajustes de débito (tarifa de POS, cancelamento de venda, etc. — qualquer
    // adjustmentType/motivo com debitType='D', ver AdjustmentRepository#sumDebitAdjustmentsBySalesSummaryId)
    // do valor líquido ANTES de dividir pelas parcelas, mesmo tratamento já dado a
    // gross/discount acima. Sem isso a ordem saía com o valor cheio mesmo quando o resumo tinha
    // ajustes vinculados que já reduziam o valor real devido (confirmado com dados reais: RV
    // 338015830, liquidValue=52,29, ajustes de débito somando exatamente 52,29 — valor real
    // devido é R$0,00, mas a ordem saía com R$52,29).
    BigDecimal netLiquidValue = orZero(summary.getLiquidValue()).subtract(orZero(debitAdjustments));
    BigDecimal releaseValue = computeInstallmentValue(netLiquidValue, installmentTotal);

    LocalDate baseDate = summary.getFirstInstallmentCreditDate() != null
      ? summary.getFirstInstallmentCreditDate()
      : summary.getRvDate();
    LocalDate releaseDate = baseDate != null
      ? adjustToPreviousBusinessDay(baseDate.plusMonths(installmentNumber - 1))
      : null;

    CreditOrderEntity co = new CreditOrderEntity();
    co.setPvCentralizer(summary.getPvNumber());
    co.setOriginalPvNumber(summary.getPvNumber());
    co.setRvNumber(summary.getRvNumber());
    co.setRvDate(summary.getRvDate());
    co.setSalesSummary(summary);
    co.setAcquirer(summary.getAcquirer());
    co.setCompany(summary.getCompany());
    co.setFlag(summary.getFlag());
    co.setBankingDomicile(summary.getBankingDomicile());
    co.setInstallmentNumber(installmentNumber);
    co.setInstallmentTotal(installmentTotal);
    co.setGrossRvValue(grossPer);
    co.setDiscountRateValue(discountPer);
    co.setReleaseValue(releaseValue);
    co.setReleaseDate(releaseDate);
    co.setCreditOrderDate(baseDate);
    co.setRecordType("MANUAL_GENERATED");
    co.setLaunchType("MANUAL");
    co.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
    co.setSalesSummaryStatus(StatusReconciliationEnum.PENDING);
    co.setReconciliationStatus(RECONCILIATION_STATUS_PENDING);
    return co;
  }

  /**
   * Recalcula creditOrderStatus/statusPaymentBank a partir de TODAS as ordens de crédito do
   * resumo (não só as recém-criadas nesta chamada) — mesmo agregado de pagamento usado por
   * BankReconciliationService/ManualBankReconciliationService (ver
   * {@link BankReconciliationService#aggregateCreditOrderPayment}). Antes, este método usava sua
   * própria regra (linhas criadas vs. installmentTotal) só pra creditOrderStatus e nunca tocava
   * statusPaymentBank — quando a última parcela faltante era criada e paga em seguida,
   * statusPaymentBank ficava "preso" no valor de antes, porque nada recalculava os dois campos
   * juntos (confirmado com dados reais: RV 44749250, 2 parcelas pagas, ficou "Reconciled"/"Parcial"
   * em vez de "Reconciled"/"Pago").
   */
  private void updateSummaryCreditOrderStatus(SalesSummaryEntity summary) {
    List<CreditOrderEntity> siblings = creditOrderRepository.findBySalesSummary_Id(summary.getId());
    BankReconciliationService.PaymentAggregate aggregate = BankReconciliationService.aggregateCreditOrderPayment(siblings);

    StatusReconciliationEnum newCoStatus;
    StatusPaymentBankEnum newPaymentStatus;
    if (aggregate.allPaid()) {
      newCoStatus = StatusReconciliationEnum.RECONCILED;
      newPaymentStatus = StatusPaymentBankEnum.PAID;
    } else if (aggregate.anyPaid()) {
      newCoStatus = StatusReconciliationEnum.PARTIALLY_RECONCILED;
      newPaymentStatus = StatusPaymentBankEnum.PARTIALLY_PAID;
    } else {
      newCoStatus = StatusReconciliationEnum.PENDING;
      newPaymentStatus = StatusPaymentBankEnum.PENDING;
    }

    if (summary.getCreditOrderStatus() != newCoStatus) {
      summary.setCreditOrderStatus(newCoStatus);
      log.info("📊 creditOrderStatus {} → {}", summary.getId(), newCoStatus);
    }
    summary.setStatusPaymentBank(newPaymentStatus);
  }

  /**
   * Fórmula compartilhada entre a geração real ({@link #buildCreditOrder}) e a prévia exibida
   * na listagem ({@link #fillNextInstallmentValue}) — divide o valor total do resumo pelo
   * número de parcelas, truncado em 2 casas, sem redistribuir o resto entre as parcelas.
   */
  private static BigDecimal computeInstallmentValue(BigDecimal totalValue, int installmentTotal) {
    BigDecimal value = orZero(totalValue);
    return installmentTotal > 1
      ? value.divide(BigDecimal.valueOf(installmentTotal), 2, RoundingMode.DOWN)
      : value;
  }

  private static BigDecimal orZero(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  /**
   * Recua para o último dia útil quando a data cai em fim de semana ou feriado cadastrado
   * (cs_holiday, específico ou recorrente — ver HolidayRepository#findActiveByDate). Confirmado
   * empiricamente com dois casos reais nos RVs 56649219/38949474, ambos gerados 1 dia a mais que
   * o esperado antes desse ajuste: a parcela 3 tem vencimento nominal em 04/07/2026 (sábado —
   * dado real da adquirente confirma liquidação em 03/07, sexta); a parcela 2 tem vencimento
   * nominal em 04/06/2026, que não é fim de semana mas está cadastrado em cs_holiday como
   * "Corpus Christi" (dia útil real seria 03/06). Visibilidade de pacote (não private) para
   * permitir teste unitário direto sem contexto Spring.
   */
  LocalDate adjustToPreviousBusinessDay(LocalDate date) {
    LocalDate candidate = date;
    while (isWeekend(candidate) || !holidayRepository.findActiveByDate(candidate).isEmpty()) {
      candidate = candidate.minusDays(1);
    }
    return candidate;
  }

  private static boolean isWeekend(LocalDate date) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
  }
}
