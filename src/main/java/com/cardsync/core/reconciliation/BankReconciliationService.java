package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;

import java.util.Map;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankReconciliationService {

  private static final int PAYMENT_PENDING = StatusPaymentBankEnum.PENDING.getCode();
  private static final int STATUS_PENDING = BankReconciliationStatus.PENDING.getCode();
  private static final int PAYMENT_PARTIAL = StatusPaymentBankEnum.PARTIALLY_PAID.getCode();
  private static final int STATUS_LIQUIDATED = BankReconciliationStatus.RECONCILED.getCode();
  private static final int STATUS_INSTALLMENT_RECONCILED = BankReconciliationStatus.INSTALLMENT_RECONCILED.getCode();

  /**
   * Gate da etapa 6: só ordens cujo resumo já foi conciliado com a ordem (etapa 5)
   * participam da conciliação com o extrato bancário.
   */
  private static final int SUMMARY_RECONCILED_STATUS = StatusReconciliationEnum.RECONCILED.getCode();

  private final EntityManager entityManager;
  private final BankReconciliationMatcher matcher;
  private final FileProcessingProperties properties;
  private final CreditOrderRepository creditOrderRepository;
  private final ReleasesBankRepository releasesBankRepository;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final TransactionErpRepository transactionErpRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final ReconciliationSettingsService reconciliationSettingsService;

  @Transactional
  public BankReconciliationResult reconcilePending() {
    return reconcilePending(BankReconciliationTriggerType.MANUAL);
  }

  @Transactional
  public BankReconciliationResult reconcilePending(BankReconciliationTriggerType trigger) {
    FileProcessingProperties.Reconciliation config = properties.getReconciliation();
    // Antes hardcoded em CREDIT_ORDER_ONLY, ignorando file-processing.reconciliation.bank-mode
    // — a configuração existia, era exposta/editável na API, mas não tinha efeito nenhum.
    BankReconciliationMode mode = config.getBankMode();
    BankReconciliationResult.Counter result = BankReconciliationResult.counter(trigger, mode);

    // Com os 3 campos desligados (default), compatible()/strength() reproduzem exatamente o
    // comportamento legado (establishment/flag opcionais, paymentKind UNKNOWN como coringa).
    ReconciliationMatchContext.MatchStrictness strictness = new ReconciliationMatchContext.MatchStrictness(
      reconciliationSettingsService.isFlagMatchRequired(),
      reconciliationSettingsService.isEstablishmentMatchRequired(),
      reconciliationSettingsService.isPaymentKindMatchRequired()
    );

    List<Object[]> eligibleRows = creditOrderRepository.findEligibleIdsGroupedByCompanyForBankReconciliation(
      SUMMARY_RECONCILED_STATUS,
      PAYMENT_PENDING,
      PAYMENT_PARTIAL,
      reconciliationSettingsService.isReprocessBankAcquirer()
    );

    int eligibleOrderCount = eligibleRows.size();
    int batchSize = Math.max(config.getBankBatchSize(), 1);
    int safeDateGapDays = reconciliationSettingsService.getDateToleranceDaysBefore()
      + reconciliationSettingsService.getDateToleranceDaysAfter()
      + 1;
    List<List<UUID>> idBatches = packIdsByCompanyIntoBatches(eligibleRows, batchSize, safeDateGapDays);
    int totalBatches = idBatches.size();

    log.info(
      "📌 Iniciando conciliação Banco x Adquirente dirigida por ordens: trigger={}, ordensElegiveis={}, tamanhoLote={}, " +
        "totalLotes={}, toleranciaAntes={}, toleranciaDepois={}, toleranciaValor={}",
      trigger.getCode(),
      eligibleOrderCount,
      batchSize,
      totalBatches,
      reconciliationSettingsService.getDateToleranceDaysBefore(),
      reconciliationSettingsService.getDateToleranceDaysAfter(),
      reconciliationSettingsService.getValueTolerance()
    );

    Set<UUID> reconciledOrderIds = new HashSet<>();
    Set<UUID> analyzedReleaseIds = new HashSet<>();

    // mode controla o que de fato roda: por padrão (CREDIT_ORDER_ONLY) o comportamento é
    // idêntico ao de antes (só ordem de crédito). Nos outros modos, a conciliação por
    // parcelas (reconcilePendingReleasesByInstallments) passa a rodar de verdade — antes
    // esse método nunca era chamado por nenhum ponto de entrada, o que também deixava
    // bank-mark-not-reconciled-after-days (usado só dentro dele) sem nenhum efeito.
    if (mode.shouldTryCreditOrders()) {
      int zeroValueCount = reconcileZeroValueOrders();
      if (zeroValueCount > 0) {
        log.info("✅ Conciliação automática: {} ordem(ns) com releaseValue zero conciliada(s).", zeroValueCount);
      }

      for (int batchNumber = 1; batchNumber <= idBatches.size(); batchNumber++) {
        List<UUID> batchIds = idBatches.get(batchNumber - 1);

        List<CreditOrderEntity> batchOrders = creditOrderRepository.findEligibleByIdsForBankReconciliation(
          batchIds,
          SUMMARY_RECONCILED_STATUS,
          PAYMENT_PENDING,
          PAYMENT_PARTIAL,
          reconciliationSettingsService.isReprocessBankAcquirer()
        );

        java.util.Map<UUID, List<CreditOrderEntity>> ordersByCompany = new java.util.LinkedHashMap<>();
        for (CreditOrderEntity order : batchOrders) {
          UUID companyId = idOrNull(order.getCompany());
          if (companyId == null) {
            log.warn(
              "⚠ Ordem ignorada por falta de empresa. creditOrder={}, releaseDate={}, releaseValue={}",
              order.getId(), order.getReleaseDate(), order.getReleaseValue()
            );
            continue;
          }
          ordersByCompany.computeIfAbsent(companyId, ignored -> new java.util.ArrayList<>()).add(order);
        }

        int reconciledBeforeBatch = result.toResult().getCreditOrdersReconciled();
        int releasesBeforeBatch = result.toResult().getReleasesReconciled();

        for (var entry : ordersByCompany.entrySet()) {
          UUID companyId = entry.getKey();
          List<CreditOrderEntity> companyOrders = entry.getValue();

          LocalDate minOrderDate = companyOrders.stream()
            .map(CreditOrderEntity::getReleaseDate)
            .filter(Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(null);
          LocalDate maxOrderDate = companyOrders.stream()
            .map(CreditOrderEntity::getReleaseDate)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(null);

          if (minOrderDate == null || maxOrderDate == null) continue;

          int toleranceDaysBefore = reconciliationSettingsService.getDateToleranceDaysBefore();
          int toleranceDaysAfter  = reconciliationSettingsService.getDateToleranceDaysAfter();
          List<ReleasesBankEntity> companyReleases = releasesBankRepository.findAvailableForCreditOrderBatch(
            STATUS_PENDING,
            reconciliationSettingsService.isReprocessBankAcquirer(),
            companyId,
            minOrderDate.minusDays(toleranceDaysBefore),
            maxOrderDate.plusDays(toleranceDaysAfter)
          );

          reconcileEligibleCreditOrders(
            companyOrders,
            companyReleases,
            reconciledOrderIds,
            analyzedReleaseIds,
            config,
            strictness,
            result
          );
        }

        entityManager.flush();
        entityManager.clear();

        BankReconciliationResult partial = result.toResult();
        log.info(
          "📦 Lote {}/{} concluído: ordensCarregadas={}, ordensConciliadasNoLote={}, releasesConciliadosNoLote={}, totalOrdensConciliadas={}",
          batchNumber,
          totalBatches,
          batchOrders.size(),
          partial.getCreditOrdersReconciled() - reconciledBeforeBatch,
          partial.getReleasesReconciled() - releasesBeforeBatch,
          partial.getCreditOrdersReconciled()
        );
      }
    }

    if (mode.shouldTryInstallmentsAfterCreditOrders() || mode.shouldTryInstallmentsFirst()) {
      reconcilePendingReleasesByInstallments(config, strictness, analyzedReleaseIds, result);
    }

    BankReconciliationResult partialResult = result.toResult();
    result.setReleasesWithoutMatch(Math.max(0, eligibleOrderCount - partialResult.getCreditOrdersReconciled()));

    BankReconciliationResult built = result.toResult();
    BigDecimal reconciledDifference = built.getTotalReleaseValueReconciled()
      .subtract(built.getTotalCreditOrderValueReconciled())
      .abs();

    log.info(
      "📘 RESUMO FINAL CONCILIAÇÃO BANCO x ADQUIRENTE: trigger={}, modo={}, ordensElegiveis={}, ordensConciliadas={}, " +
        "ordensSemMatch={}, releasesAnalisados={}, releasesConciliados={}, gruposIgnoradosLimite={}, " +
        "valorBancoConciliado={}, valorOrdensConciliado={}, diferença={}",
      built.getTrigger().getCode(),
      built.getMode(),
      eligibleOrderCount,
      built.getCreditOrdersReconciled(),
      built.getReleasesWithoutMatch(),
      built.getReleasesAnalyzed(),
      built.getReleasesReconciled(),
      built.getCandidateGroupsSkippedBySafetyCap(),
      built.getTotalReleaseValueReconciled(),
      built.getTotalCreditOrderValueReconciled(),
      reconciledDifference
    );

    logStrictModeImpactDiagnostics(built);

    return built;
  }

  /**
   * Diagnóstico de impacto do modo estrito (Fase 1 do plano de correção de conciliações
   * erradas): mede, sem alterar nenhum resultado, (a) o backlog atual de registros pendentes
   * sem bandeira/pvCentralizer/estabelecimento/modalidade conhecida, e (b) quantos matches
   * feitos NESTA execução só aconteceram por causa de um coringa. Use esses números para
   * decidir quando ligar cada toggle em ReconciliationSettingsEntity com segurança.
   */
  private void logStrictModeImpactDiagnostics(BankReconciliationResult built) {
    long ordersWithoutFlag = creditOrderRepository.countEligiblePendingWithoutFlag(
      SUMMARY_RECONCILED_STATUS, PAYMENT_PENDING, PAYMENT_PARTIAL);
    long ordersWithoutPvCentralizer = creditOrderRepository.countEligiblePendingWithoutPvCentralizer(
      SUMMARY_RECONCILED_STATUS, PAYMENT_PENDING, PAYMENT_PARTIAL);
    long ordersWithUnknownPaymentKind = creditOrderRepository.countEligiblePendingWithUnknownPaymentKind(
      SUMMARY_RECONCILED_STATUS, PAYMENT_PENDING, PAYMENT_PARTIAL);
    long releasesWithoutFlag = releasesBankRepository.countPendingWithoutFlag(STATUS_PENDING);
    long releasesWithoutEstablishment = releasesBankRepository.countPendingWithoutEstablishment(STATUS_PENDING);

    log.info(
      "🔬 DIAGNÓSTICO DE IMPACTO (regras rígidas ainda desligadas por padrão): " +
        "backlogSemBandeira[ordens={}, releases={}], backlogSemPvCentralizer[ordens={} — deveria ser ~0], " +
        "backlogSemEstabelecimento[releases={}], backlogModalidadeDesconhecida[ordens={}], " +
        "matchesNesteRunQueQuebrariamSe[bandeiraObrigatoria={}, estabelecimentoObrigatorio={}, modalidadeObrigatoria={}]",
      ordersWithoutFlag, releasesWithoutFlag, ordersWithoutPvCentralizer,
      releasesWithoutEstablishment, ordersWithUnknownPaymentKind,
      built.getOrdersMatchedRelyingOnFlagWildcard(),
      built.getOrdersMatchedRelyingOnEstablishmentWildcard(),
      built.getOrdersMatchedRelyingOnPaymentKindWildcard()
    );
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  void reconcileEligibleCreditOrders(
    List<CreditOrderEntity> eligibleOrders,
    List<ReleasesBankEntity> candidateReleases,
    Set<UUID> reconciledOrderIds,
    Set<UUID> analyzedReleaseIds,
    FileProcessingProperties.Reconciliation config,
    ReconciliationMatchContext.MatchStrictness strictness,
    BankReconciliationResult.Counter result
  ) {
    boolean reprocess = reconciliationSettingsService.isReprocessBankAcquirer();
    BigDecimal tolerance = reconciliationSettingsService.getValueTolerance();
    int toleranceDaysBefore = reconciliationSettingsService.getDateToleranceDaysBefore();
    int toleranceDaysAfter  = reconciliationSettingsService.getDateToleranceDaysAfter();
    Set<UUID> reconciledReleaseIds = new HashSet<>();

    List<CreditOrderEntity> validOrders = eligibleOrders.stream()
      .filter(order -> {
        if (hasRequiredContext(order)) return true;
        log.warn(
          "⚠ Ordem ignorada por falta de contexto. creditOrder={}, company={}, bankingDomicile={}",
          order.getId(), idOrNull(order.getCompany()), idOrNull(order.getBankingDomicile())
        );
        return false;
      })
      .toList();

    List<ReleasesBankEntity> validReleases = candidateReleases.stream()
      .filter(this::hasRequiredContext)
      .toList();

    // Pré-calcula contexto e banco de cada ordem uma única vez (O(ordens)) em vez de
    // recomputar dentro do laço de releases — antes isso rodava O(releases × ordens) vezes,
    // já que isCreditOrderCandidateCompatible recriava o contexto a cada comparação.
    Map<UUID, OrderMatchData> orderMatchDataById = new HashMap<>();
    // Indexa as ordens por data de repasse: como release e ordem só podem casar dentro de
    // uma janela de poucos dias (toleranceDaysBefore/Depois), varrer TODAS as ordens do lote
    // pra cada release é O(releases × ordens) — caro quando uma empresa concentra milhares de
    // ordens num único lote (ver packIdsByCompanyIntoBatches). Um TreeMap por data permite
    // pegar só a fatia relevante (subMap) por release, em vez do lote inteiro.
    java.util.TreeMap<LocalDate, List<CreditOrderEntity>> ordersByDate = new java.util.TreeMap<>();
    for (CreditOrderEntity order : validOrders) {
      if (order.getId() == null) continue;
      UUID orderBank = order.getBankingDomicile() != null ? idOrNull(order.getBankingDomicile().getBank()) : null;
      orderMatchDataById.put(order.getId(), new OrderMatchData(contextOf(order), orderBank));
      ordersByDate.computeIfAbsent(order.getReleaseDate(), ignored -> new java.util.ArrayList<>()).add(order);
    }

    // Flush/clear periódico: cada ordem dentro de um match dispara sua própria consulta de
    // propagação (parcelas, transações, resumo — ver applyCreditOrderMatch), e cada consulta
    // é um ponto de auto-flush do Hibernate. O auto-flush faz dirty-check + cascade em TODAS
    // as entidades ainda gerenciadas na sessão (O(n) por flush, independente de quantas
    // estejam realmente sujas ou precisem de cascade). Sem limpar com frequência, "n" cresce
    // rápido demais dentro de um único lote (múltiplas consultas por match × dezenas de
    // matches), tornando o custo total quadrático em vez de linear — confirmado via thread
    // dump preso primeiro em DirtyHelper.findDirty, depois (com um intervalo de 200 ainda
    // grande demais) em Cascade.cascade. Por isso o intervalo é bem pequeno — 1 lote inteiro
    // sem limpar já é lento demais. Entidades usadas depois de um clear() só têm campos já
    // carregados (fetch/eager) acessados, nunca coleções lazy (ver
    // updateSalesSummaryFromCreditOrder, que já usa query em vez de
    // summary.getCreditOrders()) — por isso é seguro desanexar no meio do laço.
    int matchesSinceFlush = 0;
    int matchFlushInterval = 5;

    // Acumulados no laço inteiro (não recomputados a cada match) e só aplicados uma vez no
    // final — ver comentário de recomputeReleasesAfterOrderReassignment: cada recomputo dispara
    // queries (findByReleaseBank_Id/findAllById/findBySalesSummary_Id) que forçam auto-flush do
    // Hibernate, reintroduzindo o mesmo custo O(n) por flush que o matchFlushInterval acima
    // existe pra evitar. affectedSalesSummaryIds cobre TODO match (não só realocação) — antes
    // rodava pra CADA ordem casada, ou seja, com muito mais frequência que a realocação.
    Map<UUID, Integer> reassignedCountByPreviousReleaseId = new LinkedHashMap<>();
    Set<UUID> affectedSalesSummaryIds = new HashSet<>();
    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();

    for (ReleasesBankEntity release : validReleases) {
      if (release.getId() != null && reconciledReleaseIds.contains(release.getId())) continue;

      if (release.getId() != null && analyzedReleaseIds.add(release.getId())) {
        result.releaseAnalyzed();
      }

      // reconcilePending() é uma única @Transactional cobrindo toda a execução (todos os
      // lotes/empresas) — sem isolar este try/catch, uma exceção inesperada num único
      // lançamento (ex.: dado incompleto/legado) derrubaria a transação inteira, desfazendo
      // até os matches legítimos já processados antes dele no mesmo run. O entityManager.clear()
      // no catch descarta com segurança qualquer alteração ainda não persistida deste
      // lançamento (nada foi commitado, só o flush periódico grava de fato).
      try {
        if (!processReleaseForCreditOrderMatch(
          release, ordersByDate, reconciledOrderIds, orderMatchDataById,
          toleranceDaysBefore, toleranceDaysAfter, tolerance, config, strictness, reprocess, result,
          reassignedCountByPreviousReleaseId, affectedSalesSummaryIds, affectedSalesSummaryIdsFromTransactions
        )) {
          continue;
        }
        if (release.getId() != null) reconciledReleaseIds.add(release.getId());
      } catch (RuntimeException ex) {
        log.error(
          "❌ Lançamento bancário ignorado por erro inesperado ao tentar conciliar — dado provavelmente incompleto/legado. releaseBank={}",
          release.getId(), ex
        );
        entityManager.clear();
        matchesSinceFlush = 0;
        continue;
      }

      matchesSinceFlush++;
      if (matchesSinceFlush >= matchFlushInterval) {
        entityManager.flush();
        entityManager.clear();
        matchesSinceFlush = 0;
      }
    }

    if (!reassignedCountByPreviousReleaseId.isEmpty() || !affectedSalesSummaryIds.isEmpty()
      || !affectedSalesSummaryIdsFromTransactions.isEmpty()) {
      entityManager.flush();
      if (!reassignedCountByPreviousReleaseId.isEmpty()) {
        recomputeReleasesAfterOrderReassignment(reassignedCountByPreviousReleaseId);
      }
      if (!affectedSalesSummaryIds.isEmpty()) {
        recomputeSalesSummariesFromCreditOrderIds(affectedSalesSummaryIds);
      }
      if (!affectedSalesSummaryIdsFromTransactions.isEmpty()) {
        recomputeSalesSummariesFromTransactionIds(affectedSalesSummaryIdsFromTransactions);
      }
    }
  }

  /**
   * Avalia um único lançamento contra as ordens de crédito candidatas e, se compatível, aplica
   * o match. Extraído do laço principal para poder ser isolado num try/catch por lançamento
   * (ver comentário em {@link #reconcileEligibleCreditOrders}) — uma exceção aqui não deve
   * derrubar os demais lançamentos da mesma execução.
   *
   * @return true se um match foi aplicado nesta chamada, false se não houver candidato
   *         compatível ou nenhum subconjunto bater o valor.
   */
  private boolean processReleaseForCreditOrderMatch(
    ReleasesBankEntity release,
    java.util.TreeMap<LocalDate, List<CreditOrderEntity>> ordersByDate,
    Set<UUID> reconciledOrderIds,
    Map<UUID, OrderMatchData> orderMatchDataById,
    int toleranceDaysBefore,
    int toleranceDaysAfter,
    BigDecimal tolerance,
    FileProcessingProperties.Reconciliation config,
    ReconciliationMatchContext.MatchStrictness strictness,
    boolean reprocess,
    BankReconciliationResult.Counter result,
    Map<UUID, Integer> reassignedCountByPreviousReleaseId,
    Set<UUID> affectedSalesSummaryIds,
    Set<UUID> affectedSalesSummaryIdsFromTransactions
  ) {
    ReconciliationMatchContext releaseContext = contextOf(release);
    UUID releaseBank = idOrNull(release.getBank());

    // Janela válida: order.releaseDate entre (release.releaseDate - toleranceDaysAfter) e
    // (release.releaseDate + toleranceDaysBefore) — ver isCreditOrderCandidateCompatible.
    LocalDate windowFrom = release.getReleaseDate().minusDays(toleranceDaysAfter);
    LocalDate windowTo = release.getReleaseDate().plusDays(toleranceDaysBefore);
    List<CreditOrderEntity> ordersInWindow = ordersByDate.subMap(windowFrom, true, windowTo, true)
      .values().stream()
      .flatMap(List::stream)
      .toList();

    List<CreditOrderEntity> compatible = ordersInWindow.stream()
      .filter(order -> isOrderStillEligible(order, reconciledOrderIds, reprocess))
      .filter(order -> isCreditOrderCandidateCompatible(
        release, releaseContext, releaseBank,
        order, orderMatchDataById.get(order.getId()),
        toleranceDaysBefore, toleranceDaysAfter, strictness
      ))
      .toList();

    if (compatible.isEmpty()) return false;

    BankReconciliationMatcher.MatchResult selected = matcher.selectByValue(
      compatible,
      CreditOrderEntity::getReleaseValue,
      release.getReleaseValue(),
      tolerance,
      config.getSafeCapCents(),
      reconciliationSettingsService.getSubsetDpMaxCents()
    );

    if (selected.skippedBySafetyCap()) {
      result.candidateGroupSkippedBySafetyCap();
    }
    if (!selected.matched()) return false;

    List<CreditOrderEntity> orders = selected.typedItems();
    trackStrictModeImpact(releaseContext, orders, orderMatchDataById, result);
    applyCreditOrderMatch(
      release, orders, selected, result, reprocess,
      reassignedCountByPreviousReleaseId, affectedSalesSummaryIds, affectedSalesSummaryIdsFromTransactions
    );
    orders.stream().map(CreditOrderEntity::getId).filter(Objects::nonNull).forEach(reconciledOrderIds::add);
    return true;
  }

  /**
   * Diagnóstico (não altera nenhum resultado de matching): entre as ordens que ACABARAM de
   * casar de verdade com este release, conta quantas só casaram porque bandeira/estabelecimento/
   * modalidade estavam nulos/desconhecidos em algum lado — ou seja, quantas deixariam de casar
   * automaticamente se a regra correspondente (ReconciliationSettingsEntity.*MatchRequired)
   * estivesse ligada nesta mesma execução. Ver BankReconciliationResult.
   */
  private void trackStrictModeImpact(
    ReconciliationMatchContext releaseContext,
    List<CreditOrderEntity> matchedOrders,
    Map<UUID, OrderMatchData> orderMatchDataById,
    BankReconciliationResult.Counter result
  ) {
    for (CreditOrderEntity order : matchedOrders) {
      OrderMatchData data = orderMatchDataById.get(order.getId());
      if (data == null) continue;
      ReconciliationMatchContext orderContext = data.context();
      if (releaseContext.flagId() == null || orderContext.flagId() == null) {
        result.matchedOrderRelyingOnFlagWildcard();
      }
      if (releaseContext.establishmentPv() == null || orderContext.establishmentPv() == null) {
        result.matchedOrderRelyingOnEstablishmentWildcard();
      }
      if (releaseContext.paymentKind() == ReconciliationMatchContext.PaymentKind.UNKNOWN
        || orderContext.paymentKind() == ReconciliationMatchContext.PaymentKind.UNKNOWN) {
        result.matchedOrderRelyingOnPaymentKindWildcard();
      }
    }
  }

  /**
   * Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto
   * Spring. {@code reassignedCountByPreviousReleaseId} é acumulado pelo chamador ao longo do
   * laço inteiro de {@link #reconcileEligibleCreditOrders} e recomputado numa única passada no
   * final — ver comentário lá sobre por que não recomputar aqui, por match.
   */
  void applyCreditOrderMatch(
    ReleasesBankEntity release,
    List<CreditOrderEntity> orders,
    BankReconciliationMatcher.MatchResult selected,
    BankReconciliationResult.Counter result,
    boolean reprocess,
    Map<UUID, Integer> reassignedCountByPreviousReleaseId,
    Set<UUID> affectedSalesSummaryIds,
    Set<UUID> affectedSalesSummaryIdsFromTransactions
  ) {
    BankReconciliationMatchType matchType = BankReconciliationMatchType.creditOrderByCount(orders.size());

    // Com reprocess=true, uma ordem já vinculada a OUTRO lançamento pode ser realocada para
    // este aqui (ver isOrderStillEligible). Sem isto, o lançamento antigo nunca é revisitado —
    // fica com numberCreditOrders/reconciliationStatus presos ao valor anterior, podendo até
    // continuar "PAID" sem nenhuma ordem real vinculada (ver recomputeReleasesAfterOrderReassignment).
    for (CreditOrderEntity order : orders) {
      ReleasesBankEntity previousRelease = order.getReleaseBank();
      if (previousRelease != null && previousRelease.getId() != null
        && !previousRelease.getId().equals(release.getId())) {
        reassignedCountByPreviousReleaseId.merge(previousRelease.getId(), 1, Integer::sum);
      }
      order.setReleaseBank(release);
      order.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      order.setReconciliationStatus(STATUS_LIQUIDATED);
      order.setCreditStatus(STATUS_LIQUIDATED);
      // Coleta o id em vez de chamar updateSalesSummaryFromCreditOrder aqui — essa chamada
      // fazia uma query por ORDEM (não só por realocação, ao contrário do bloco acima), rodando
      // centenas de vezes por lote e sendo o principal responsável pela lentidão observada em
      // produção (auto-flush do Hibernate disparado a cada query). Recompute em lote no final.
      if (order.getSalesSummary() != null && order.getSalesSummary().getId() != null) {
        affectedSalesSummaryIds.add(order.getSalesSummary().getId());
      }
    }
    propagateCreditOrdersToInstallments(orders, release, reprocess);

    release.setNumberCreditOrders(orders.size());
    release.setNumberReconciliations(safeInt(release.getNumberReconciliations()) + orders.size());
    release.setReconciliationStatus(StatusPaymentBankEnum.PAID);

    creditOrderRepository.saveAll(orders);
    releasesBankRepository.save(release);

    result.matchedByCreditOrders(orders.size(), selected.matchedValue());
    result.releaseReconciled(release.getReleaseValue());
    result.transactionsUpdated(propagateReleaseStatusTransactions(release, affectedSalesSummaryIdsFromTransactions));

    log.debug(
      "✅ Ordem(ns) de pagamento conciliada(s) com lançamento bancário. ordemInicial={}, releaseBank={}, tipoMatch={}, ordens={}, valorRelease={}, valorOrdens={}",
      orders.getFirst().getId(), release.getId(), matchType, orders.size(), release.getReleaseValue(), selected.matchedValue()
    );
  }

  /**
   * Recalcula, a partir da contagem real restante (não por incremento/decremento em memória),
   * os lançamentos que perderam ordem(ns) para outro lançamento num reprocessamento. Se um
   * lançamento fica sem nenhuma ordem de crédito e sem nenhuma parcela vinculada, volta a
   * PENDING — do contrário continuaria "conciliado" sem nada de verdade por trás. Mesma lógica
   * de {@link #recomputeSalesSummariesFromCreditOrders}, aplicada ao lado do lançamento bancário.
   */
  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  void recomputeReleasesAfterOrderReassignment(Map<UUID, Integer> reassignedCountByPreviousReleaseId) {
    for (ReleasesBankEntity previousRelease : releasesBankRepository.findAllById(reassignedCountByPreviousReleaseId.keySet())) {
      int reassignedCount = reassignedCountByPreviousReleaseId.getOrDefault(previousRelease.getId(), 0);
      List<CreditOrderEntity> remainingOrders = creditOrderRepository.findByReleaseBank_Id(previousRelease.getId());
      boolean stillHasInstallments = !installmentAcqRepository.findByReleaseBank_Id(previousRelease.getId()).isEmpty();

      previousRelease.setNumberCreditOrders(remainingOrders.size());
      previousRelease.setNumberReconciliations(
        Math.max(0, safeInt(previousRelease.getNumberReconciliations()) - reassignedCount));
      if (remainingOrders.isEmpty() && !stillHasInstallments) {
        previousRelease.setReconciliationStatus(StatusPaymentBankEnum.PENDING);
      }
      releasesBankRepository.save(previousRelease);

      log.info(
        "⚠ Lançamento perdeu {} ordem(ns) para outro lançamento em reprocessamento — contador recalculado. " +
          "releaseBank={}, ordensRestantes={}, statusFinal={}",
        reassignedCount, previousRelease.getId(), remainingOrders.size(), previousRelease.getReconciliationStatus()
      );
    }
  }

  private void reconcilePendingReleasesByInstallments(
    FileProcessingProperties.Reconciliation config,
    ReconciliationMatchContext.MatchStrictness strictness,
    Set<UUID> analyzedReleaseIds,
    BankReconciliationResult.Counter result
  ) {
    List<ReleasesBankEntity> releases = releasesBankRepository.findForBankReconciliation(
      STATUS_PENDING,
      reconciliationSettingsService.isReprocessBankAcquirer()
    );

    // Acumulado pelo laço inteiro e recomputado uma única vez no final — mesmo motivo de
    // reconcileEligibleCreditOrders/applyCreditOrderMatch.
    Set<UUID> affectedSalesSummaryIdsFromTransactions = new HashSet<>();

    for (ReleasesBankEntity release : releases) {
      if (release.getId() != null && analyzedReleaseIds.add(release.getId())) {
        result.releaseAnalyzed();
      }
      if (!hasRequiredContext(release)) {
        markReleaseNotReconciledWhenExpired(release, config, "contexto bancário obrigatório ausente", result);
        result.releaseSkippedMissingContext();
        continue;
      }

      BankReconciliationMatcher.MatchResult installmentResult = reconcileByInstallmentsWithStats(release, config, strictness, result);
      if (installmentResult.matched()) {
        result.releaseReconciled(release.getReleaseValue());
        result.transactionsUpdated(propagateReleaseStatusTransactions(release, affectedSalesSummaryIdsFromTransactions));
      } else {
        markReleaseNotReconciledWhenExpired(release, config, "nenhuma parcela compatível encontrada", result);
      }
    }

    if (!affectedSalesSummaryIdsFromTransactions.isEmpty()) {
      entityManager.flush();
      recomputeSalesSummariesFromTransactionIds(affectedSalesSummaryIdsFromTransactions);
    }
  }

  private int reconcileZeroValueOrders() {
    List<CreditOrderEntity> orders = creditOrderRepository
      .findPendingZeroValueOrders(PAYMENT_PENDING, BigDecimal.ZERO);
    if (orders.isEmpty()) return 0;
    for (CreditOrderEntity order : orders) {
      order.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      order.setReconciliationStatus(STATUS_LIQUIDATED);
      order.setCreditStatus(STATUS_LIQUIDATED);
      updateSalesSummaryFromCreditOrder(order);
    }
    creditOrderRepository.saveAll(orders);
    return orders.size();
  }

  /**
   * Desfaz a conciliação de um lançamento bancário: as ordens de crédito e
   * parcelas vinculadas voltam ao estado anterior (pendentes), o próprio
   * lançamento volta a PENDING, e os resumos de venda afetados têm seu status
   * recalculado a partir do que sobrar reconciliado (caso o resumo tenha
   * outras ordens ligadas a lançamentos diferentes). Não altera o status da
   * TransactionAcqEntity/TransactionErpEntity (pertence a outra etapa da
   * esteira), o vínculo resumo↔ordem (salesSummaryStatus da ordem, etapa 6)
   * nem nenhum dado importado do arquivo — só o que a conciliação bancária
   * (etapa 7) escreveu.
   */
  @Transactional
  public UndoBankReconciliationResult undoReconciliation(UUID releaseBankId) {
    ReleasesBankEntity release = releasesBankRepository.findById(releaseBankId)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "bank.release.not.found"));

    List<CreditOrderEntity> orders = creditOrderRepository.findByReleaseBank_Id(releaseBankId);
    List<InstallmentAcqEntity> installments = installmentAcqRepository.findByReleaseBank_Id(releaseBankId);

    if (orders.isEmpty() && installments.isEmpty()) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "bank.release.not.reconciled");
    }

    Set<UUID> affectedSummaryIds = new HashSet<>();
    for (CreditOrderEntity order : orders) {
      order.setReleaseBank(null);
      order.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
      order.setReconciliationStatus(BankReconciliationStatus.PENDING.getCode());
      order.setCreditStatus(BankReconciliationStatus.PENDING.getCode());
      if (order.getSalesSummary() != null && order.getSalesSummary().getId() != null) {
        affectedSummaryIds.add(order.getSalesSummary().getId());
      }
    }
    creditOrderRepository.saveAll(orders);

    for (InstallmentAcqEntity installment : installments) {
      installment.setReleaseBank(null);
      installment.setPaymentDate(null);
      installment.setStatusPaymentBank(BankReconciliationStatus.PENDING.getCode());
      installment.setInstallmentStatus(StatusInstallmentEnum.SCHEDULED.getCode());
      installment.setReconciliationBankLine(null);
      installment.setReconciliationBankFile(null);
      installment.setReconciliationBankProcessedAt(null);
    }
    installmentAcqRepository.saveAll(installments);

    recomputeSalesSummariesFromCreditOrders(orders, affectedSummaryIds);

    release.setReconciliationStatus(StatusPaymentBankEnum.PENDING);
    release.setNumberCreditOrders(0);
    release.setNumberReconciliations(Math.max(0, safeInt(release.getNumberReconciliations()) - orders.size()));
    release.setDivergenceValue(null);
    release.setDivergenceReason(null);
    releasesBankRepository.save(release);

    log.info(
      "↩ Conciliação desfeita. releaseBank={}, ordensDesvinculadas={}, parcelasDesvinculadas={}",
      releaseBankId, orders.size(), installments.size()
    );

    return new UndoBankReconciliationResult(orders.size(), installments.size());
  }

  /**
   * Recalcula creditOrderStatus/statusPaymentBank dos resumos de venda afetados,
   * a partir de TODAS as ordens de crédito ligadas a cada resumo (não só as que
   * foram revertidas) — cobre o caso de um resumo com ordens conciliadas via
   * outro lançamento bancário, que devem permanecer intactas.
   */
  private void recomputeSalesSummariesFromCreditOrders(List<CreditOrderEntity> resetOrders, Set<UUID> affectedSummaryIds) {
    if (affectedSummaryIds.isEmpty()) return;

    Map<UUID, SalesSummaryEntity> summaries = new HashMap<>();
    for (CreditOrderEntity order : resetOrders) {
      SalesSummaryEntity summary = order.getSalesSummary();
      if (summary != null && summary.getId() != null && affectedSummaryIds.contains(summary.getId())) {
        summaries.putIfAbsent(summary.getId(), summary);
      }
    }

    for (SalesSummaryEntity summary : summaries.values()) {
      List<CreditOrderEntity> siblings = List.copyOf(summary.getCreditOrders());
      PaymentAggregate aggregate = aggregateCreditOrderPayment(siblings);

      if (aggregate.allPaid()) {
        summary.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      } else if (aggregate.anyPaid()) {
        summary.setCreditOrderStatus(StatusReconciliationEnum.PARTIALLY_RECONCILED);
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PARTIALLY_PAID);
      } else {
        summary.setCreditOrderStatus(StatusReconciliationEnum.PENDING);
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
      }
    }
  }

  /** Público para permitir reuso em ManualBankReconciliationService e CreditOrderManualService (pacote irmão). */
  public record PaymentAggregate(boolean allPaid, boolean anyPaid) {
  }

  /**
   * "Todas pagas" só quando o número de parcelas PAGAS bate com o total de parcelas ESPERADO
   * (CreditOrderEntity#installmentTotal) — não com o número de CreditOrder que já existem. Uma
   * parcela ainda não gerada (ver SaleSummarySpecs#missingCreditOrdersSpec, que existe
   * justamente pra achar esse gap) não pode contar como "paga" só porque as parcelas que já
   * existem estão todas pagas; do contrário o resumo vira "Pago" com uma parcela ainda faltando
   * ser criada (confirmado com dados reais: RV 8549241 tinha as parcelas 1 e 3 pagas, faltando a
   * 2, e ainda assim aparecia como "Pago"). Sem candidatas, expectedTotal cai pro tamanho de
   * siblings (mesmo comportamento de antes). Público para reuso em ManualBankReconciliationService
   * e CreditOrderManualService (pacote irmão com.cardsync.core.reconciliation.summary) — este
   * último usava sua própria regra (linhas criadas vs. installmentTotal) só pra creditOrderStatus,
   * nunca tocando statusPaymentBank; unificado aqui pra ambos os campos sempre refletirem o mesmo
   * agregado de pagamento, evitando o desalinhamento entre os dois campos (ver RV 44749250).
   */
  public static PaymentAggregate aggregateCreditOrderPayment(List<CreditOrderEntity> siblings) {
    if (siblings.isEmpty()) return new PaymentAggregate(false, false);

    int expectedTotal = siblings.stream()
      .map(CreditOrderEntity::getInstallmentTotal)
      .filter(Objects::nonNull)
      .max(Integer::compareTo)
      .orElse(siblings.size());

    long paidCount = siblings.stream()
      .filter(co -> StatusPaymentBankEnum.PAID.equals(co.getStatusPaymentBank()))
      .count();

    return new PaymentAggregate(paidCount == expectedTotal, paidCount > 0);
  }

  /**
   * Empacota os ids elegíveis (agrupados por empresa, ordenados por data dentro de cada
   * empresa) em lotes de tamanho aproximado {@code batchSize}. Antes o corte era por posição
   * numa lista global (todas as empresas misturadas, desempate por id aleatório), o que podia
   * separar, entre lotes diferentes, ordens de crédito que pertencem ao mesmo lançamento
   * bancário — fazendo a conciliação nunca encontrar a combinação correta em nenhum dos dois
   * lotes.

   * Corrige isso sem simplesmente tratar cada empresa como um bloco indivisível (o que faria
   * uma empresa com muito volume virar um único lote gigante, sem os flush/clear intermediários
   * do EntityManager entre lotes): dentro de cada empresa, só corta um novo lote quando a
   * lacuna entre duas ordens consecutivas (por data) é maior que {@code safeDateGapDays} — ou
   * seja, maior que a janela de tolerância de data usada no matching. Isso garante que nenhuma
   * ordem que possa cair na janela de tolerância de um mesmo lançamento fique num lote
   * diferente do resto do seu grupo, mantendo os lotes perto do tamanho configurado.
   */
  private List<List<UUID>> packIdsByCompanyIntoBatches(List<Object[]> eligibleRows, int batchSize, int safeDateGapDays) {
    Map<UUID, List<Object[]>> rowsByCompany = new java.util.LinkedHashMap<>();
    for (Object[] row : eligibleRows) {
      UUID companyId = (UUID) row[0];
      rowsByCompany.computeIfAbsent(companyId, ignored -> new java.util.ArrayList<>()).add(row);
    }

    List<List<UUID>> chunks = new java.util.ArrayList<>();
    for (List<Object[]> companyRows : rowsByCompany.values()) {
      List<UUID> currentChunk = new java.util.ArrayList<>();
      LocalDate previousDate = null;
      for (Object[] row : companyRows) {
        UUID orderId = (UUID) row[1];
        LocalDate releaseDate = (LocalDate) row[2];
        boolean safeToCut = previousDate != null
          && ChronoUnit.DAYS.between(previousDate, releaseDate) > safeDateGapDays;
        if (safeToCut && currentChunk.size() >= batchSize) {
          chunks.add(currentChunk);
          currentChunk = new java.util.ArrayList<>();
        }
        currentChunk.add(orderId);
        previousDate = releaseDate;
      }
      if (!currentChunk.isEmpty()) chunks.add(currentChunk);
    }

    List<List<UUID>> batches = new java.util.ArrayList<>();
    List<UUID> current = new java.util.ArrayList<>();
    for (List<UUID> chunk : chunks) {
      if (!current.isEmpty() && current.size() + chunk.size() > batchSize) {
        batches.add(current);
        current = new java.util.ArrayList<>();
      }
      current.addAll(chunk);
    }
    if (!current.isEmpty()) batches.add(current);
    return batches;
  }

  private boolean isOrderStillEligible(CreditOrderEntity order, Set<UUID> reconciledOrderIds, boolean reprocess) {
    if (order == null) return false;
    if (!reprocess && order.getReleaseBank() != null) return false;
    if (order.getId() != null && reconciledOrderIds.contains(order.getId())) return false;
    if (order.getReleaseDate() == null || order.getReleaseValue() == null) return false;
    return order.getReleaseValue().compareTo(BigDecimal.ZERO) > 0;
  }

  private boolean hasRequiredContext(CreditOrderEntity order) {
    return order != null
      && order.getReleaseDate() != null
      && order.getReleaseValue() != null
      && order.getCompany() != null
      && order.getCompany().getId() != null
      && order.getAcquirer() != null
      && order.getAcquirer().getId() != null;
  }

  private BankReconciliationMatcher.MatchResult reconcileByInstallmentsWithStats(
    ReleasesBankEntity release,
    FileProcessingProperties.Reconciliation config,
    ReconciliationMatchContext.MatchStrictness strictness,
    BankReconciliationResult.Counter result
  ) {
    BankReconciliationMatcher.MatchResult installmentResult = reconcileByInstallments(release, config, strictness);
    if (installmentResult.skippedBySafetyCap()) {
      result.candidateGroupSkippedBySafetyCap();
    }
    if (installmentResult.matched()) {
      result.matchedByInstallments(installmentResult.itemsMatched(), installmentResult.matchedValue());
    }
    return installmentResult;
  }

  private BankReconciliationMatcher.MatchResult reconcileByInstallments(
    ReleasesBankEntity release,
    FileProcessingProperties.Reconciliation config,
    ReconciliationMatchContext.MatchStrictness strictness
  ) {
    int toleranceDaysBefore = reconciliationSettingsService.getDateToleranceDaysBefore();
    int toleranceDaysAfter  = reconciliationSettingsService.getDateToleranceDaysAfter();
    BigDecimal valueTolerance = reconciliationSettingsService.getValueTolerance();
    // dateFrom: installments expected up to toleranceDaysAfter before the release (release came after)
    // dateTo:   installments expected up to toleranceDaysBefore after the release (release came before)
    LocalDate dateFrom = release.getReleaseDate().minusDays(toleranceDaysAfter);
    LocalDate dateTo = release.getReleaseDate().plusDays(toleranceDaysBefore);

    ReconciliationMatchContext releaseContext = contextOf(release);
    List<InstallmentAcqEntity> candidates = installmentAcqRepository.findPendingForBankRelease(
        STATUS_PENDING,
        release.getCompany().getId(),
        idOrNull(release.getAcquirer()),
        idOrNull(release.getEstablishment()),
        idOrNull(release.getBankingDomicile()),
        idOrNull(release.getFlag()),
        dateFrom,
        dateTo
      ).stream()
      .filter(installment -> isInstallmentCandidateCompatible(release, installment, toleranceDaysBefore, toleranceDaysAfter, strictness))
      .sorted(Comparator.comparingInt(
        (InstallmentAcqEntity installment) -> releaseContext.strength(contextOf(installment), strictness)).reversed())
      .toList();

    BankReconciliationMatcher.MatchResult selected = matcher.selectByValue(
      candidates,
      this::netInstallmentValue,
      release.getReleaseValue(),
      valueTolerance,
      config.getSafeCapCents(),
      reconciliationSettingsService.getSubsetDpMaxCents()
    );

    if (!selected.matched()) return selected;

    List<InstallmentAcqEntity> installments = selected.typedItems();
    BankReconciliationMatchType matchType = BankReconciliationMatchType.installmentByCount(installments.size());
    applyReleaseToInstallments(installments, release);

    release.setNumberParcels(installments.size());
    release.setNumberReconciliations(safeInt(release.getNumberReconciliations()) + installments.size());
    release.setReconciliationStatus(StatusPaymentBankEnum.PAID);

    installmentAcqRepository.saveAll(installments);
    releasesBankRepository.save(release);

    log.info(
      "✅ Release bancário conciliado por parcelas. releaseBank={}, tipoMatch={}, parcelas={}, valorRelease={}, valorParcelas={}",
      release.getId(), matchType, installments.size(), release.getReleaseValue(), selected.matchedValue()
    );
    return selected;
  }

  private void propagateCreditOrdersToInstallments(List<CreditOrderEntity> orders, ReleasesBankEntity release, boolean reprocess) {
    // Agrupa por acquirerId → { rvNumber → installmentNumber } para busca em lote
    Map<UUID, Map<Integer, Integer>> acquirerRvToInstNum = new java.util.LinkedHashMap<>();
    for (CreditOrderEntity order : orders) {
      if (order.getAcquirer() == null || order.getRvNumber() == null || order.getInstallmentNumber() == null) continue;
      acquirerRvToInstNum
        .computeIfAbsent(order.getAcquirer().getId(), k -> new java.util.LinkedHashMap<>())
        .put(order.getRvNumber(), order.getInstallmentNumber());
    }
    if (acquirerRvToInstNum.isEmpty()) return;

    List<InstallmentAcqEntity> allInstallments = new java.util.ArrayList<>();
    for (var entry : acquirerRvToInstNum.entrySet()) {
      UUID acquirerId = entry.getKey();
      Map<Integer, Integer> rvToInstNum = entry.getValue();
      List<InstallmentAcqEntity> batch = installmentAcqRepository
        .findByAcquirerIdAndRvNumbers(acquirerId, rvToInstNum.keySet(), reprocess);
      for (InstallmentAcqEntity ia : batch) {
        if (ia.getTransaction() == null) continue;
        Integer rv = ia.getTransaction().getRvNumber();
        Integer expectedInst = rv != null ? rvToInstNum.get(rv) : null;
        if (expectedInst != null && expectedInst.equals(ia.getInstallment())) {
          allInstallments.add(ia);
        }
      }
    }

    if (allInstallments.isEmpty()) {
      log.debug("⏳ Nenhuma parcela ADQ encontrada para propagar. release={}, ordens={}", release.getId(), orders.size());
      return;
    }

    applyReleaseToInstallments(allInstallments, release);
    installmentAcqRepository.saveAll(allInstallments);
    log.debug("✅ {} parcela(s) ADQ propagada(s) para {} ordem(ns). release={}", allInstallments.size(), orders.size(), release.getId());
  }

  private void applyReleaseToInstallments(List<InstallmentAcqEntity> installments, ReleasesBankEntity release) {
    OffsetDateTime now = OffsetDateTime.now();
    for (InstallmentAcqEntity installment : installments) {
      installment.setReleaseBank(release);
      installment.setPaymentDate(release.getReleaseDate());
      installment.setStatusPaymentBank(STATUS_LIQUIDATED);
      installment.setInstallmentStatus(STATUS_INSTALLMENT_RECONCILED);
      installment.setReconciliationBankLine(release.getLineNumber());
      installment.setReconciliationBankProcessedAt(now);
      installment.setReconciliationBankFile(release.getProcessedFile());
      if (installment.getCreditOrder() != null) {
        installment.getCreditOrder().setReleaseBank(release);
        installment.getCreditOrder().setStatusPaymentBank(StatusPaymentBankEnum.PAID);
        installment.getCreditOrder().setReconciliationStatus(STATUS_LIQUIDATED);
        installment.getCreditOrder().setCreditStatus(STATUS_LIQUIDATED);
      }
    }
  }

  private int propagateReleaseStatusTransactions(ReleasesBankEntity release, Set<UUID> affectedSalesSummaryIdsFromTransactions) {
    if (release.getId() == null) return 0;
    List<InstallmentAcqEntity> linkedInstallments = installmentAcqRepository.findByReleaseBank_Id(release.getId());
    if (linkedInstallments.isEmpty()) return 0;

    Set<UUID> transactionIds = linkedInstallments.stream()
      .map(InstallmentAcqEntity::getTransaction)
      .filter(Objects::nonNull)
      .map(TransactionAcqEntity::getId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    Map<UUID, List<InstallmentAcqEntity>> installmentsByTx = installmentAcqRepository
      .findByTransactionIdIn(transactionIds).stream()
      .filter(i -> i.getTransaction() != null && i.getTransaction().getId() != null)
      .collect(Collectors.groupingBy(i -> i.getTransaction().getId()));

    Map<UUID, TransactionErpEntity> erpByTxId = transactionErpRepository
      .findByTransactionAcqIdIn(transactionIds).stream()
      .filter(e -> e.getTransactionAcq() != null && e.getTransactionAcq().getId() != null)
      .collect(Collectors.toMap(e -> e.getTransactionAcq().getId(), e -> e, (a, b) -> a));

    Set<UUID> updatedTransactions = new HashSet<>();
    for (InstallmentAcqEntity linked : linkedInstallments) {
      TransactionAcqEntity transaction = linked.getTransaction();
      if (transaction == null || transaction.getId() == null || updatedTransactions.contains(transaction.getId())) continue;
      List<InstallmentAcqEntity> txInstallments = installmentsByTx.getOrDefault(transaction.getId(), List.of());
      updateStatusTransactionBatched(transaction, txInstallments, erpByTxId.get(transaction.getId()), affectedSalesSummaryIdsFromTransactions);
      updatedTransactions.add(transaction.getId());
    }
    return updatedTransactions.size();
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  void updateStatusTransactionBatched(
    TransactionAcqEntity transaction,
    List<InstallmentAcqEntity> installments,
    TransactionErpEntity erpTx,
    Set<UUID> affectedSalesSummaryIdsFromTransactions
  ) {
    if (transaction == null || installments.isEmpty()) return;

    boolean allLiquidated = installments.stream().allMatch(i -> Objects.equals(i.getStatusPaymentBank(), STATUS_LIQUIDATED));
    boolean anyLiquidated = installments.stream().anyMatch(i -> Objects.equals(i.getStatusPaymentBank(), STATUS_LIQUIDATED));

    if (allLiquidated) {
      transaction.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      transaction.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);
    } else if (anyLiquidated) {
      transaction.setStatusPaymentBank(StatusPaymentBankEnum.DIVERGENT);
    } else {
      transaction.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
      transaction.setStatusTransaction(StatusTransactionEnum.PENDING);
    }

    // Coleta o id em vez de recomputar o resumo aqui — ver recomputeSalesSummariesFromTransactionIds
    // (mesmo motivo do recompute em lote de ordens de crédito: uma query por TRANSAÇÃO, rodando
    // centenas/milhares de vezes por lote, era o principal custo de performance em produção).
    SalesSummaryEntity summary = transaction.getSalesSummary();
    if (summary != null && summary.getId() != null) {
      affectedSalesSummaryIdsFromTransactions.add(summary.getId());
    }

    if (erpTx != null) {
      if (allLiquidated) {
        erpTx.setStatusTransaction(StatusTransactionEnum.AUTOMATICALLY_RECONCILED);
      } else if (!anyLiquidated) {
        erpTx.setStatusTransaction(StatusTransactionEnum.PENDING);
      }
    }
  }

  /**
   * Recalcula statusPaymentBank do resumo a partir de TODAS as transações ADQ ligadas a ele —
   * antes, este método copiava direto o status de UMA única transação (a última processada no
   * lote) para o resumo inteiro, sem olhar as demais. Num resumo parcelado (várias
   * TransactionAcqEntity, uma por parcela) isso deixava o status do resumo dependente da ordem
   * de processamento, e uma transação com liquidação parcial (DIVERGENT — algumas parcelas já
   * bateram no banco, outras ainda não, o normal enquanto um parcelamento ainda está sendo pago)
   * virava "Divergente" no resumo inteiro mesmo quando as demais transações já estavam PAID.
   * Mesmo padrão de {@link #updateSalesSummaryFromCreditOrder}/{@link #recomputeSalesSummariesFromCreditOrderIds},
   * mas com uma única query para todos os resumos afetados em vez de uma por resumo.
   */
  void recomputeSalesSummariesFromTransactionIds(Set<UUID> salesSummaryIds) {
    if (salesSummaryIds.isEmpty()) return;

    Map<UUID, List<TransactionAcqEntity>> transactionsBySummaryId = transactionAcqRepository
      .findBySalesSummary_IdIn(salesSummaryIds).stream()
      .filter(tx -> tx.getSalesSummary() != null && tx.getSalesSummary().getId() != null)
      .collect(Collectors.groupingBy(tx -> tx.getSalesSummary().getId()));

    for (var entry : transactionsBySummaryId.entrySet()) {
      List<TransactionAcqEntity> siblings = entry.getValue();
      SalesSummaryEntity summary = siblings.getFirst().getSalesSummary();

      boolean allPaid = !siblings.isEmpty() && siblings.stream()
        .allMatch(tx -> StatusPaymentBankEnum.PAID.equals(tx.getStatusPaymentBank()));
      // DIVERGENT conta como "algum pagamento" — é uma transação com liquidação parcial, não um erro.
      boolean anyPaid = siblings.stream()
        .anyMatch(tx -> StatusPaymentBankEnum.PAID.equals(tx.getStatusPaymentBank())
          || StatusPaymentBankEnum.DIVERGENT.equals(tx.getStatusPaymentBank()));

      if (allPaid) {
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      } else if (anyPaid) {
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PARTIALLY_PAID);
      } else {
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PENDING);
      }
    }
  }

  /**
   * Recalcula creditOrderStatus/statusPaymentBank do resumo a partir de TODAS as ordens de
   * crédito ligadas a ele (não só a que acabou de casar) — um resumo parcelado pode ter
   * várias CreditOrderEntity (uma por parcela), cada uma liquidada em lançamentos bancários
   * diferentes. Marcar o resumo inteiro como RECONCILED/PAID assim que uma única parcela
   * bate esconderia parcelas irmãs ainda pendentes. Mesma lógica de
   * {@link #recomputeSalesSummariesFromCreditOrders}, usada no caminho de desfazer.
   */
  private void updateSalesSummaryFromCreditOrder(CreditOrderEntity order) {
    SalesSummaryEntity summary = order.getSalesSummary();
    if (summary == null || summary.getId() == null) return;

    List<CreditOrderEntity> siblings = creditOrderRepository.findBySalesSummary_Id(summary.getId());
    PaymentAggregate aggregate = aggregateCreditOrderPayment(siblings);

    if (aggregate.allPaid()) {
      summary.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);
      summary.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
    } else if (aggregate.anyPaid()) {
      summary.setCreditOrderStatus(StatusReconciliationEnum.PARTIALLY_RECONCILED);
      summary.setStatusPaymentBank(StatusPaymentBankEnum.PARTIALLY_PAID);
    }
  }

  /**
   * Mesma lógica de {@link #updateSalesSummaryFromCreditOrder}, mas para vários resumos de uma
   * vez: uma única query ({@code findBySalesSummary_IdIn}) busca as ordens de TODOS os resumos
   * afetados, em vez de uma query por resumo. Usada no laço de conciliação bancária (ver
   * {@link #reconcileEligibleCreditOrders}), onde recomputar por ordem individual — centenas de
   * vezes por lote — se mostrou o principal custo de performance em produção.
   */
  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  void recomputeSalesSummariesFromCreditOrderIds(Set<UUID> salesSummaryIds) {
    if (salesSummaryIds.isEmpty()) return;

    Map<UUID, List<CreditOrderEntity>> ordersBySummaryId = creditOrderRepository
      .findBySalesSummary_IdIn(salesSummaryIds).stream()
      .filter(co -> co.getSalesSummary() != null && co.getSalesSummary().getId() != null)
      .collect(Collectors.groupingBy(co -> co.getSalesSummary().getId()));

    for (var entry : ordersBySummaryId.entrySet()) {
      List<CreditOrderEntity> siblings = entry.getValue();
      SalesSummaryEntity summary = siblings.getFirst().getSalesSummary();
      PaymentAggregate aggregate = aggregateCreditOrderPayment(siblings);

      if (aggregate.allPaid()) {
        summary.setCreditOrderStatus(StatusReconciliationEnum.RECONCILED);
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PAID);
      } else if (aggregate.anyPaid()) {
        summary.setCreditOrderStatus(StatusReconciliationEnum.PARTIALLY_RECONCILED);
        summary.setStatusPaymentBank(StatusPaymentBankEnum.PARTIALLY_PAID);
      }
    }
  }

  /**
   * Recebe o contexto/banco do release e da ordem já pré-calculados pelo chamador — evita
   * recriar {@link ReconciliationMatchContext} (e refazer paymentKind/idOrNull) a cada par
   * release×ordem, quando release e ordem já são fixos por toda a duração do laço externo.
   */
  /** Visibilidade de pacote (não private) para permitir reuso em PreImplantationDivergenceReconciliationService. */
  boolean isCreditOrderCandidateCompatible(
    ReleasesBankEntity release, ReconciliationMatchContext releaseContext, UUID releaseBank,
    CreditOrderEntity order, OrderMatchData orderData,
    int toleranceDaysBefore, int toleranceDaysAfter, ReconciliationMatchContext.MatchStrictness strictness
  ) {
    if (!passesDateAndBankChecks(release, releaseBank, order, orderData, toleranceDaysBefore, toleranceDaysAfter)) return false;
    return releaseContext.compatible(orderData.context(), strictness);
  }

  /**
   * Mesma checagem acima, mas ignorando estabelecimento — usada pelo {@link CreditOrderCandidateFinder}
   * (ferramentas de análise: divergência pré-implantação, legado sem ordem) apenas como FALLBACK,
   * quando o PV do próprio lançamento não tem nenhuma candidata direta via
   * {@link #isCreditOrderCandidateCompatible}. Confirmado com o financeiro: alguns bancos (ex.:
   * Santander) consolidam num único lançamento os valores de mais de um estabelecimento (PV) da
   * mesma empresa — exigir o mesmo PV sempre descartaria candidatas legítimas nesse cenário. Mas
   * usar isso como caminho ÚNICO (em vez de fallback) quebra o cenário oposto: múltiplos
   * lançamentos do mesmo banco/adquirente/bandeira no mesmo dia, um por PV (não consolidados) —
   * o pool ignorando estabelecimento somaria candidatas de todos os PVs pra cada lançamento,
   * mascarando uma divergência real como falso excesso. O matcher automático (Etapa 7) continua
   * estabelecimento-consciente via {@link #isCreditOrderCandidateCompatible}, para não arriscar
   * vínculos automáticos indevidos entre PVs diferentes quando a consolidação não se aplica.
   */
  boolean isCreditOrderCandidateCompatibleIgnoringEstablishment(
    ReleasesBankEntity release, ReconciliationMatchContext releaseContext, UUID releaseBank,
    CreditOrderEntity order, OrderMatchData orderData,
    int toleranceDaysBefore, int toleranceDaysAfter, ReconciliationMatchContext.MatchStrictness strictness
  ) {
    if (!passesDateAndBankChecks(release, releaseBank, order, orderData, toleranceDaysBefore, toleranceDaysAfter)) return false;
    return releaseContext.compatibleIgnoringEstablishment(orderData.context(), strictness);
  }

  /**
   * Checagem de data + contexto normal (empresa/adquirente/bandeira/modalidade), SEM exigir
   * banco — usada só pelo diagnóstico de domicílio bancário divergente (ver
   * BankingDomicileDivergenceService), nunca pelo matcher automático nem para vincular sozinha:
   * o arquivo da adquirente pode legitimamente apontar o banco errado numa RV específica (visto
   * com dados reais: RV 86015456, Rede declarou Santander, mas o repasse caiu no Sicredi), então
   * o resultado deste método serve só para apontar candidatas pra revisão humana, nunca para
   * vínculo automático.
   */
  boolean isCreditOrderCandidateCompatibleIgnoringBank(
    ReleasesBankEntity release, ReconciliationMatchContext releaseContext,
    CreditOrderEntity order, OrderMatchData orderData,
    int toleranceDaysBefore, int toleranceDaysAfter, ReconciliationMatchContext.MatchStrictness strictness
  ) {
    if (order == null || order.getReleaseValue() == null || order.getReleaseDate() == null || orderData == null) return false;
    long daysDiff = ChronoUnit.DAYS.between(order.getReleaseDate(), release.getReleaseDate());
    if (daysDiff > toleranceDaysAfter) return false;
    if (daysDiff < -toleranceDaysBefore) return false;
    return releaseContext.compatible(orderData.context(), strictness)
      || releaseContext.compatibleIgnoringEstablishment(orderData.context(), strictness);
  }

  private boolean passesDateAndBankChecks(
    ReleasesBankEntity release, UUID releaseBank, CreditOrderEntity order, OrderMatchData orderData,
    int toleranceDaysBefore, int toleranceDaysAfter
  ) {
    if (order == null || order.getReleaseValue() == null || order.getReleaseDate() == null || orderData == null) return false;
    // daysDiff > 0: lançamento DEPOIS da ordem (normal); < 0: lançamento ANTES da ordem (suspeito)
    long daysDiff = ChronoUnit.DAYS.between(order.getReleaseDate(), release.getReleaseDate());
    if (daysDiff > toleranceDaysAfter) return false;
    if (daysDiff < -toleranceDaysBefore) return false;
    // Banco obrigatório: release.bank vs order.bankingDomicile.bank
    return releaseBank != null && orderData.bankId() != null && releaseBank.equals(orderData.bankId());
  }

  /**
   * Contexto de matching e banco (via domicílio bancário) de uma ordem, pré-calculados uma única
   * vez por ordem. Visibilidade de pacote (não private) para permitir reuso em
   * PreImplantationDivergenceReconciliationService.
   */
  record OrderMatchData(ReconciliationMatchContext context, UUID bankId) {}

  private boolean isInstallmentCandidateCompatible(
    ReleasesBankEntity release, InstallmentAcqEntity installment,
    int toleranceDaysBefore, int toleranceDaysAfter, ReconciliationMatchContext.MatchStrictness strictness
  ) {
    if (installment == null || installment.getExpectedPaymentDate() == null) return false;
    if (installment.getTransaction() != null && installment.getTransaction().getSaleDate() != null) {
      LocalDate saleDate = installment.getTransaction().getSaleDate().toLocalDate();
      if (release.getReleaseDate().isBefore(saleDate)) return false;
    }
    // daysDiff > 0: lançamento DEPOIS do pagamento esperado (normal); < 0: lançamento ANTES
    long daysDiff = ChronoUnit.DAYS.between(installment.getExpectedPaymentDate(), release.getReleaseDate());
    if (daysDiff > toleranceDaysAfter) return false;
    if (daysDiff < -toleranceDaysBefore) return false;
    return contextOf(release).compatible(contextOf(installment), strictness);
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  ReconciliationMatchContext contextOf(ReleasesBankEntity release) {
    EstablishmentEntity establishment = release.getEstablishment();
    // modality_payment_bank é NULL-ável no banco (dado legado/importação parcial) —
    // ModalityPaymentBankEnum.fromCode(null) retorna null, e getModalityPaymentBank() propaga
    // esse null. Sem esta guarda, .getCode() lançava NPE que derrubava a execução inteira
    // (reconcilePending é uma única @Transactional, sem isolamento por lote/release).
    ModalityPaymentBankEnum modalityPaymentBank = release.getModalityPaymentBank();
    return new ReconciliationMatchContext(
      idOrNull(release.getCompany()),
      idOrNull(release.getAcquirer()),
      idOrNull(establishment),
      establishment != null ? establishment.getPvNumber() : null,
      idOrNull(release.getFlag()),
      paymentKindFromBank(
        modalityPaymentBank != null ? modalityPaymentBank.getCode() : null,
        release.getDescriptionHistoricalBank(), release.getComplementRelease(), release.getDocumentComplementNumber())
    );
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  ReconciliationMatchContext contextOf(CreditOrderEntity order) {
    return new ReconciliationMatchContext(
      idOrNull(order.getCompany()),
      idOrNull(order.getAcquirer()),
      null,
      // CreditOrderEntity não tem relação com EstablishmentEntity — pvCentralizer é o
      // identificador de estabelecimento já usado com esse propósito na Etapa 4
      // (CreditOrderOrphanLinkingService: acquirer + pvCentralizer + rvNumber).
      order.getPvCentralizer(),
      idOrNull(order.getFlag()),
      paymentKindFromCreditOrder(order)
    );
  }

  private ReconciliationMatchContext contextOf(InstallmentAcqEntity installment) {
    TransactionAcqEntity tx = installment.getTransaction();
    if (tx == null) {
      return new ReconciliationMatchContext(null, null, null, null, null, ReconciliationMatchContext.PaymentKind.UNKNOWN);
    }
    EstablishmentEntity establishment = tx.getEstablishment();
    return new ReconciliationMatchContext(
      idOrNull(tx.getCompany()),
      idOrNull(tx.getAcquirer()),
      idOrNull(establishment),
      establishment != null ? establishment.getPvNumber() : null,
      idOrNull(tx.getFlag()),
      paymentKindFromTransaction(tx)
    );
  }

  /**
   * order.getTransactionType() vem bruto da posição 93-94 do EEFI (Rede) e codifica
   * "à vista (1) vs. parcelado (2-5)" — não débito/crédito. Todo pedido de crédito
   * importado do EEFI é, por natureza, uma transação de CRÉDITO (débito à vista
   * liquida via EEVD, sem passar por CreditOrder). Por isso derivamos o tipo de
   * pagamento da modalidade real do resumo de vendas vinculado, igual ao caminho
   * de geração manual (ver transactionTypeFromSummary), em vez do campo bruto.
   */
  private ReconciliationMatchContext.PaymentKind paymentKindFromCreditOrder(CreditOrderEntity order) {
    SalesSummaryEntity summary = order.getSalesSummary();
    if (summary == null) return ReconciliationMatchContext.PaymentKind.UNKNOWN;
    return paymentKindFromModality(summary.getModality());
  }

  private ReconciliationMatchContext.PaymentKind paymentKindFromTransaction(TransactionAcqEntity tx) {
    return paymentKindFromModality(tx.getModality());
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  ReconciliationMatchContext.PaymentKind paymentKindFromModality(Integer modalityCode) {
    ModalityEnum modality = ModalityEnum.fromCode(modalityCode);
    if (modality == ModalityEnum.CASH_DEBIT) return ReconciliationMatchContext.PaymentKind.DEBIT;
    if (modality == ModalityEnum.CASH_CREDIT
      || modality == ModalityEnum.INSTALLMENT_CREDIT_2_6
      || modality == ModalityEnum.INSTALLMENT_CREDIT_7_12
      || modality == ModalityEnum.INSTALLMENT_CREDIT_13_21
      // DIGITAL_WALLET e OUTROS são modalidades de cartão de crédito reais (ver
      // ModalityResolver, ProcessRedeEeVcService/ProcessRedeEeVdService) — antes caíam em
      // UNKNOWN, que age como coringa em ReconciliationMatchContext.compatible() e permitia
      // essas ordens casarem indevidamente com lançamentos bancários de DÉBITO.
      || modality == ModalityEnum.DIGITAL_WALLET
      || modality == ModalityEnum.OUTROS) {
      return ReconciliationMatchContext.PaymentKind.CREDIT;
    }
    return ReconciliationMatchContext.PaymentKind.UNKNOWN;
  }

  private ReconciliationMatchContext.PaymentKind paymentKindFromBank(Integer modalityPaymentBank, String... textParts) {
    if (modalityPaymentBank != null) {
      if (modalityPaymentBank == 1) return ReconciliationMatchContext.PaymentKind.DEBIT;
      if (modalityPaymentBank == 2) return ReconciliationMatchContext.PaymentKind.CREDIT;
    }
    String text = String.join(" ", textParts == null ? new String[0] : textParts).toUpperCase();
    if (text.contains("DEBIT") || text.contains("DÉBIT") || text.contains("DEB ") || text.contains("ELECTRON") || text.contains("MAESTRO")) {
      return ReconciliationMatchContext.PaymentKind.DEBIT;
    }
    if (text.contains("CRED") || text.contains("CRÉD") || text.contains("VISA") || text.contains("MASTER") || text.contains("ELO") || text.contains("AMEX")) {
      return ReconciliationMatchContext.PaymentKind.CREDIT;
    }
    return ReconciliationMatchContext.PaymentKind.UNKNOWN;
  }

  private boolean hasRequiredContext(ReleasesBankEntity release) {
    return release.getReleaseDate() != null
      && release.getReleaseValue() != null
      && release.getCompany() != null
      && release.getCompany().getId() != null
      && release.getBank() != null
      && release.getBank().getId() != null;
  }

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  void markReleaseNotReconciledWhenExpired(
    ReleasesBankEntity release,
    FileProcessingProperties.Reconciliation config,
    String reason,
    BankReconciliationResult.Counter result
  ) {
    if (!shouldMarkNotReconciled(release, config)) {
      result.releaseKeptPending();
      log.debug(
        "⏳ Release bancário mantido pendente. releaseBank={}, data={}, valor={}, motivo={}",
        release.getId(), release.getReleaseDate(), release.getReleaseValue(), reason
      );
      return;
    }

    // getReconciliationStatus() retorna o enum convertido (StatusPaymentBankEnum), não o int
    // bruto — comparar com STATUS_PENDING (int) via Objects.equals nunca era verdadeiro (tipos
    // incompatíveis), então este bloco praticamente nunca executava para o caso comum (release
    // realmente PENDING). Corrigido comparando contra o enum StatusPaymentBankEnum.PENDING.
    if (release.getReconciliationStatus() == null
      || Objects.equals(release.getReconciliationStatus(), StatusPaymentBankEnum.PENDING)) {
      // NOT_PAID (BankReconciliationStatus.NOT_RECONCILED) — não PAID, que é o status de match
      // real (ver applyCreditOrderMatch/reconcileByInstallments). Usar PAID aqui mascarava um
      // lançamento sem nenhuma ordem/parcela vinculada como "conciliado", excluindo-o para
      // sempre de findAvailableForCreditOrderBatch/findForBankReconciliation (que só trazem
      // releases PENDING quando reprocess=false) mesmo que uma ordem elegível pudesse casar
      // com ele numa execução futura.
      release.setReconciliationStatus(StatusPaymentBankEnum.NOT_PAID);
      releasesBankRepository.save(release);
    }
    result.releaseWithoutMatch();
    log.info(
      "⚠ Release bancário marcado como não conciliado. releaseBank={}, data={}, valor={}, motivo={}",
      release.getId(), release.getReleaseDate(), release.getReleaseValue(), reason
    );
  }

  private boolean shouldMarkNotReconciled(ReleasesBankEntity release, FileProcessingProperties.Reconciliation config) {
    int days = Math.max(reconciliationSettingsService.getBankMarkNotReconciledAfterDays(), 0);
    if (release.getReleaseDate() == null) return true;
    LocalDate limitDate = LocalDate.now().minusDays(days);
    return !release.getReleaseDate().isAfter(limitDate);
  }

  private BigDecimal netInstallmentValue(InstallmentAcqEntity installment) {
    BigDecimal value = nvl(installment.getLiquidValue());
    if (installment.getAdjustmentValue() != null) {
      value = value.subtract(installment.getAdjustmentValue());
    }
    return value;
  }

  private BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private int safeInt(Integer value) {
    return value == null ? 0 : value;
  }

  /**
   * Antes usava reflexão ({@code getClass().getMethod("getId").invoke(...)}) sem cache do
   * Method — chamado a cada comparação release×ordem dentro de {@code contextOf}/
   * {@code isCreditOrderCandidateCompatible}, ou seja, potencialmente milhões de vezes por
   * execução. Com lotes maiores (empresas de alto volume não são mais fatiadas por posição
   * fixa) isso se tornou o gargalo real da Etapa 7. Todas as entidades usadas aqui estendem
   * {@link com.cardsync.domain.model.AuditableEntityBase}, que já expõe {@code getId()}
   * tipado via Lombok — chamar direto elimina a reflexão.
   */
  private UUID idOrNull(com.cardsync.domain.model.AuditableEntityBase entity) {
    return entity == null ? null : entity.getId();
  }
}