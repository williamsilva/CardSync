package com.cardsync.core.management.dashboard;

import com.cardsync.bff.controller.v1.representation.model.management.AuditSalesSummaryModel;
import com.cardsync.bff.controller.v1.representation.model.management.AuditSalesSummaryModel.AuditSaleRow;
import com.cardsync.bff.controller.v1.representation.model.management.AuditSalesSummaryModel.AuditSalesDetail;
import com.cardsync.bff.controller.v1.representation.model.management.AuditUnreconciledModel;
import com.cardsync.bff.controller.v1.representation.model.management.AuditUnreconciledModel.AcquirerGroup;
import com.cardsync.bff.controller.v1.representation.model.management.AuditUnreconciledModel.DayDetail;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.filter.ConciliationWaitingModelFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusTransactionEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.infrastructure.repository.spec.ConciliationWaitingAcqSpecs;
import com.cardsync.infrastructure.repository.spec.ConciliationWaitingErpSpecs;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Auditoria do dashboard: vendas (TransactionAcq) consolidadas por dia.

 * Retorna os últimos {@value #DAYS} dias COM registro de venda (não dias de
 * calendário): um resumo geral por dia (todas as adquirentes somadas) e o
 * detalhamento por adquirente, cada uma com suas linhas diárias. Valor = soma do
 * bruto; cvCount = quantidade de CVs (transações).
 */
@Service
@RequiredArgsConstructor
public class DashboardAuditService {

  private static final int DAYS = 10;
  private static final int MONEY_SCALE = 2;
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final EntityManager entityManager;
  private final ImplantationDateProvider implantationDateProvider;
  private final DateFilterService dateFilterService;
  private final AcquirerRepository acquirerRepository;
  private final ConciliationWaitingErpSpecs conciliationWaitingErpSpecs;
  private final ConciliationWaitingAcqSpecs conciliationWaitingAcqSpecs;

  @Transactional(readOnly = true)
  public AuditSalesSummaryModel salesSummary() {
    // Janela fixa: os últimos 10 dias de calendário (hoje e os 9 anteriores).
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    List<LocalDate> allDays = new ArrayList<>();
    for (int i = 0; i < DAYS; i++) {
      allDays.add(today.minusDays(i)); // do mais recente para o mais antigo
    }

    String offset = currentBusinessOffset();

    LocalDate minDay = today.minusDays(DAYS - 1L);
    LocalDate implantationDate = implantationDateProvider.get();
    LocalDate effectiveMin = minDay.isBefore(implantationDate) ? implantationDate : minDay;
    OffsetDateTime start = effectiveMin.atStartOfDay().atOffset(ZoneOffset.UTC);
    OffsetDateTime end = today.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

    List<Integer> excludedStatuses = List.of(
      StatusTransactionEnum.DELETED.getCode(),
      StatusTransactionEnum.CANCELED.getCode()
    );

    // CONVERT_TZ (MySQL) não existe no Postgres; o equivalente nativo é a função
    // timezone(zone, timestamp), que corresponde ao operador "timestamp AT TIME ZONE zone".
    // Aninhado (UTC -> offset de negócio) reproduz exatamente o CONVERT_TZ(col, '+00:00', offset).
    //
    // offset entra como literal (não bind parameter) de propósito: currentBusinessOffset() só
    // gera "[+-]HH:MM" (sem entrada de usuário, sem risco de injection), e o Postgres — ao
    // contrário do MySQL — exige que a expressão do GROUP BY seja estruturalmente idêntica à do
    // SELECT. Como :offset repetido 3x vira 3 bind parameters distintos ($1/$4/$7), o Postgres
    // não reconhece a expressão do GROUP BY como igual à do SELECT mesmo com o mesmo valor em
    // runtime. Com o literal embutido, as 3 ocorrências ficam textualmente idênticas.
    String offsetLiteral = "'" + offset + "'";
    Query q = entityManager.createQuery(
      "select function('date', function('timezone', " + offsetLiteral + ", function('timezone', '+00:00', t.saleDate))) as dia, " +
        "       t.acquirer.fantasyName as adquirente, " +
        "       coalesce(sum(t.grossValue),0), " +
        "       count(t.id) " +
        "  from TransactionAcqEntity t " +
        " where t.saleDate >= :start and t.saleDate <= :end " +
        "   and (t.statusTransaction is null or t.statusTransaction not in :excludedStatuses) " +
        "   and (t.modality is null or t.modality <> :excludedModality) " +
        " group by function('date', function('timezone', " + offsetLiteral + ", function('timezone', '+00:00', t.saleDate))), t.acquirer.fantasyName " +
        " order by function('date', function('timezone', " + offsetLiteral + ", function('timezone', '+00:00', t.saleDate))) desc, t.acquirer.fantasyName asc"
    );
    q.setParameter("start", start);
    q.setParameter("end", end);
    q.setParameter("excludedStatuses", excludedStatuses);
    q.setParameter("excludedModality", ModalityEnum.DIGITAL_WALLET.getCode());

    List<Object[]> rows = q.getResultList();

    // Consolidação geral por dia (todas as adquirentes).
    Map<LocalDate, DayTotal> summaryByDay = new LinkedHashMap<>();
    // Detalhe por adquirente -> (dia -> total).
    Map<String, Map<LocalDate, DayTotal>> byAcquirer = new LinkedHashMap<>();

    for (Object[] r : rows) {
      LocalDate day = toLocalDate(r[0]);
      String acquirer = r[1] != null ? r[1].toString() : "—";
      BigDecimal value = money(r[2]);
      long cvCount = ((Number) r[3]).longValue();

      summaryByDay.computeIfAbsent(day, d -> new DayTotal())
        .add(value, cvCount);

      byAcquirer.computeIfAbsent(acquirer, a -> new LinkedHashMap<>())
        .computeIfAbsent(day, d -> new DayTotal())
        .add(value, cvCount);
    }

    // summary: um registro por dia da janela (0 quando não houve venda).
    List<AuditSaleRow> summary = allDays.stream()
      .map(day -> rowFor(day, summaryByDay.get(day)))
      .toList();

    // acquirerDetails: cada adquirente com os 10 dias preenchidos, em ordem decrescente.
    List<AuditSalesDetail> acquirerDetails = new ArrayList<>();
    for (Map.Entry<String, Map<LocalDate, DayTotal>> entry : byAcquirer.entrySet()) {
      List<AuditSaleRow> detailRows = allDays.stream()
        .map(day -> rowFor(day, entry.getValue().get(day)))
        .toList();
      acquirerDetails.add(new AuditSalesDetail(entry.getKey(), detailRows));
    }

    return new AuditSalesSummaryModel(summary, acquirerDetails);
  }

  /**
   * Reaproveita os MESMOS specs das telas /missing-acquirer e /missing-erp
   * ({@code fromQueryForTotals} = baseFilters), garantindo que a regra de "não
   * conciliada" seja única. Qualquer alteração futura nesses specs reflete aqui
   * automaticamente. A agregação (count por adquirente + dia) é montada via
   * CriteriaBuilder aplicando o predicate do spec.
   */
  @Transactional(readOnly = true)
  public AuditUnreconciledModel unreconciled(ListQueryDto<ConciliationWaitingModelFilter> query) {
    // ONLY_IN_ERP: mesma definição de /missing-acquirer.
    List<Object[]> erpRows = aggregateByAcquirerAndDay(
      TransactionErpEntity.class,
      conciliationWaitingErpSpecs.fromQueryForTotals(query));

    // ONLY_IN_ACQUIRER: mesma definição de /missing-erp.
    List<Object[]> acqRows = aggregateByAcquirerAndDay(
      TransactionAcqEntity.class,
      conciliationWaitingAcqSpecs.fromQueryForTotals(query));

    // Acumula por adquirente -> (dia -> contadores).
    Map<UUID, AcquirerAccumulator> byAcquirer = new LinkedHashMap<>();

    // Semeia com as adquirentes relevantes.
    // Se o filtro especificar adquirentes, semeia apenas elas; caso contrário, semeia todas
    // para que as sem pendência apareçam zeradas (count = 0, details = []).
    List<UUID> filteredAcquirerIds = filteredAcquirerIds(query);
    List<com.cardsync.domain.model.AcquirerEntity> acquirersToSeed = filteredAcquirerIds.isEmpty()
      ? acquirerRepository.findAll()
      : acquirerRepository.findAllById(filteredAcquirerIds);

    for (var acquirer : acquirersToSeed) {
      if (acquirer.getId() != null) {
        byAcquirer.put(
          acquirer.getId(),
          new AcquirerAccumulator(acquirer.getFantasyName() != null ? acquirer.getFantasyName() : "—")
        );
      }
    }

    for (Object[] r : erpRows) {
      UUID acqId = (UUID) r[0];
      LocalDate day = toLocalDate(r[1]);
      long count = ((Number) r[2]).longValue();
      if (day == null) continue;
      byAcquirer.computeIfAbsent(acqId, id -> new AcquirerAccumulator("—"))
        .day(day).onlyInErp += count;
    }

    for (Object[] r : acqRows) {
      UUID acqId = (UUID) r[0];
      LocalDate day = toLocalDate(r[1]);
      long count = ((Number) r[2]).longValue();
      if (day == null) continue;
      byAcquirer.computeIfAbsent(acqId, id -> new AcquirerAccumulator("—"))
        .day(day).onlyInAcquirer += count;
    }

    long total = 0;
    List<AcquirerGroup> groups = new ArrayList<>();
    for (Map.Entry<UUID, AcquirerAccumulator> entry : byAcquirer.entrySet()) {
      AcquirerAccumulator acc = entry.getValue();

      long acquirerCount = 0;
      List<DayDetail> details = new ArrayList<>();
      // Ordena os dias de forma crescente.
      List<LocalDate> days = new ArrayList<>(acc.byDay.keySet());
      days.sort(LocalDate::compareTo);
      for (LocalDate day : days) {
        DayCounters c = acc.byDay.get(day);
        long dayTotal = c.erpAcq + c.onlyInErp + c.onlyInAcquirer;
        acquirerCount += dayTotal;
        details.add(new DayDetail(formatDate(day), c.erpAcq, c.onlyInErp, c.onlyInAcquirer));
      }

      total += acquirerCount;
      groups.add(new AcquirerGroup(entry.getKey(), acc.name, acquirerCount, details));
    }

    return new AuditUnreconciledModel(total, groups);
  }

  private static final class AcquirerAccumulator {
    private final String name;
    private final Map<LocalDate, DayCounters> byDay = new LinkedHashMap<>();

    AcquirerAccumulator(String name) {
      this.name = name;
    }

    DayCounters day(LocalDate day) {
      return byDay.computeIfAbsent(day, d -> new DayCounters());
    }
  }

  private static final class DayCounters {
    private long erpAcq = 0;
    private long onlyInErp = 0;
    private long onlyInAcquirer = 0;
  }

  private List<UUID> filteredAcquirerIds(ListQueryDto<ConciliationWaitingModelFilter> query) {
    if (query == null || query.advanced() == null) return List.of();
    List<String> acquirers = query.advanced().acquirers();
    if (acquirers == null || acquirers.isEmpty()) return List.of();
    return acquirers.stream()
      .filter(s -> s != null && !s.isBlank())
      .map(s -> { try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; } })
      .filter(Objects::nonNull)
      .toList();
  }

  /**
   * Conta registros por (acquirer.id, dia da saleDate) aplicando o predicate do spec
   * informado — o mesmo usado pelas telas de pendência. Retorna linhas [acquirerId, dia, count].
   */
  private <T> List<Object[]> aggregateByAcquirerAndDay(Class<T> entityClass, Specification<T> spec) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
    Root<T> root = cq.from(entityClass);

    // LEFT JOIN: não descarta transações sem adquirente (acquirer_id null),
    // que o count das telas conta normalmente. Um path implícito faria INNER JOIN.
    Join<Object, Object> acquirerJoin =
      root.join("acquirer", JoinType.LEFT);
    Path<Object> acquirerId = acquirerJoin.get("id");

    // O saleDate é armazenado em UTC. Para agrupar pelo DIA no fuso de negócio
    // (ex.: vendas após 21:00 BRT não devem cair no dia seguinte), convertemos a
    // coluna de UTC para o offset do fuso de negócio ANTES de extrair a data —
    // o mesmo critério que os specs usam para o filtro de data.
    // CONVERT_TZ (MySQL) não existe no Postgres; timezone(zone, timestamp) é o equivalente
    // nativo do operador "timestamp AT TIME ZONE zone". Aninhado (UTC -> offset de negócio)
    // reproduz exatamente o CONVERT_TZ(col, '+00:00', offset).
    String offset = currentBusinessOffset();
    Expression<java.sql.Timestamp> utcTs =
      cb.function("timezone", java.sql.Timestamp.class,
        cb.literal("+00:00"), root.get("saleDate"));
    Expression<java.sql.Timestamp> localTs =
      cb.function("timezone", java.sql.Timestamp.class,
        cb.literal(offset), utcTs);

    Expression<java.sql.Date> day = cb.function("date", java.sql.Date.class, localTs);

    cq.multiselect(acquirerId, day, cb.count(root));

    Predicate predicate =
      spec != null ? spec.toPredicate(root, cq, cb) : null;
    if (predicate != null) {
      cq.where(predicate);
    }

    cq.groupBy(acquirerId, day);
    return entityManager.createQuery(cq).getResultList();
  }

  /**
   * Offset atual do fuso de negócio no formato aceito por timezone()/AT TIME ZONE do
   * Postgres (ex.: "-03:00"). Usa o offset vigente (Brasil não tem mais horário de verão,
   * então é estável), evitando depender de nomes de timezone (ex. "America/Sao_Paulo").
   */
  private String currentBusinessOffset() {
    ZoneId zone = dateFilterService.businessZone();
    ZoneOffset zoneOffset = zone.getRules().getOffset(java.time.Instant.now());
    int totalSeconds = zoneOffset.getTotalSeconds();
    String sign = totalSeconds < 0 ? "-" : "+";
    int abs = Math.abs(totalSeconds);
    int hours = abs / 3600;
    int minutes = (abs % 3600) / 60;
    return String.format("%s%02d:%02d", sign, hours, minutes);
  }

  private AuditSaleRow rowFor(LocalDate day, DayTotal total) {
    if (total == null) {
      return new AuditSaleRow(formatDate(day), BigDecimal.ZERO.setScale(MONEY_SCALE), 0);
    }
    return new AuditSaleRow(formatDate(day), total.value(), total.cvCount());
  }

  private static final class DayTotal {
    private BigDecimal value = BigDecimal.ZERO;
    private long cvCount = 0;

    void add(BigDecimal v, long c) {
      this.value = this.value.add(v);
      this.cvCount += c;
    }

    BigDecimal value() {
      return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    long cvCount() {
      return cvCount;
    }
  }

  private LocalDate toLocalDate(Object raw) {
    if (raw instanceof java.sql.Date sqlDate) {
      return sqlDate.toLocalDate();
    }
    if (raw instanceof LocalDate localDate) {
      return localDate;
    }
    if (raw instanceof java.util.Date date) {
      return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }
    return LocalDate.parse(raw.toString());
  }

  private String formatDate(LocalDate date) {
    return date.format(DATE_FMT);
  }

  private BigDecimal money(Object value) {
    if (value == null) return BigDecimal.ZERO.setScale(MONEY_SCALE);
    return new BigDecimal(value.toString()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }
}