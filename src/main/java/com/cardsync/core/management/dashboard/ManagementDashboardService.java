package com.cardsync.core.management.dashboard;

import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardModel;
import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardModel.DebitSummary;
import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardModel.DebitsBlock;
import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardModel.FeesBlock;
import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardModel.FeesRow;
import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardModel.SalesBlock;
import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardModel.SalesRow;
import com.cardsync.bff.controller.v1.representation.model.management.ManagementDashboardRequest;
import com.cardsync.domain.model.enums.AdjustmentReasonEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.PeriodEnum;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serviço de agregação do dashboard de gerenciamento.
 *
 * Fontes:
 * - sales e fees: TransactionAcqEntity (vendas da adquirente).
 * - payments: CreditOrderEntity (ordens de crédito / recebível bancário).
 * - debits: AdjustmentEntity (cancelamentos, taxas e chargebacks por adjustmentReason).
 *
 * Agrupamento dinâmico por seção: COMPANY, ACQUIRER, MODALITY, FLAG ou DATE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementDashboardService {

  private final EntityManager entityManager;
  private final com.cardsync.infrastructure.repository.spec.config.DateFilterService dateFilterService;

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int MONEY_SCALE = 2;
  private static final int RATE_SCALE = 2;

  // Classificação de débitos por motivo do ajuste (consistente com os specs existentes).
  private static final List<Integer> CANCELLATION_REASONS = List.of(
    AdjustmentReasonEnum.CANCEL_VENDAS.getCode(),
    AdjustmentReasonEnum.CANCEL_VENDA_DEBITO.getCode()
  );
  private static final List<Integer> FEE_REASONS = List.of(
    AdjustmentReasonEnum.TX_MAN_TEF.getCode(),
    AdjustmentReasonEnum.TARIFA_CBK.getCode(),
    AdjustmentReasonEnum.NAO_TOKENIZADAS.getCode(),
    AdjustmentReasonEnum.SALES_ANTICIPATION.getCode(),
    AdjustmentReasonEnum.POS_INATIV_CONEC_PIN.getCode(),
    AdjustmentReasonEnum.TRF_AD_EXCESSO_CBACK.getCode(),
    AdjustmentReasonEnum.AL_POS_PINPAD_TX_CONECT.getCode()
  );
  private static final List<Integer> CHARGEBACK_REASONS = List.of(
    AdjustmentReasonEnum.SALE_DISPUTE.getCode(),
    AdjustmentReasonEnum.CHARGEBACK.getCode(),
    AdjustmentReasonEnum.CANCEL_CHBK_MAESTRO.getCode()
  );

  @Transactional(readOnly = true)
  public ManagementDashboardModel dashboard(ManagementDashboardRequest request) {
    ManagementDashboardRequest req = request != null ? request : emptyRequest();

    Dimension salesDim = Dimension.resolve(groupByOrDefault(req, GroupSection.SALES));
    Dimension paymentsDim = Dimension.resolve(groupByOrDefault(req, GroupSection.PAYMENTS));
    Dimension feesDim = Dimension.resolve(groupByOrDefault(req, GroupSection.FEES));
    Dimension debitsDim = Dimension.resolve(groupByOrDefault(req, GroupSection.DEBITS));

    return new ManagementDashboardModel(
      buildSales(req, salesDim),
      buildPayments(req, paymentsDim),
      buildFees(req, feesDim),
      buildDebits(req, debitsDim)
    );
  }

  // ---------------------------------------------------------------------------
  // SALES (TransactionAcq): bruto (primary) x líquido a receber (secondary)
  // ---------------------------------------------------------------------------
  private SalesBlock buildSales(ManagementDashboardRequest req, Dimension dim) {
    String labelExpr = dim.labelExpr("t");
    String groupExpr = dim.groupExpr("t");

    StringBuilder jpql = new StringBuilder(
      "select " + labelExpr + " as lbl, " +
        "count(t.id), " +
        "coalesce(sum(t.grossValue),0), " +
        "coalesce(sum(t.discountValue),0), " +
        "coalesce(sum(t.liquidValue),0) " +
        "from TransactionAcqEntity t "
    );
    List<String> where = baseWhere("t", req, "t.saleDate", true);
    appendWhere(jpql, where);
    jpql.append(" group by ").append(groupExpr).append(" order by ").append(labelExpr);

    Query q = entityManager.createQuery(jpql.toString());
    bindParams(q, req, true, false);

    List<Object[]> rows = q.getResultList();

    List<String> labels = new ArrayList<>();
    List<BigDecimal> primary = new ArrayList<>();
    List<BigDecimal> secondary = new ArrayList<>();
    List<SalesRow> tableRows = new ArrayList<>();

    BigDecimal totalValue = rows.stream()
      .map(r -> money(r[2]))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    for (Object[] r : rows) {
      String label = label(r[0], dim);
      long tx = ((Number) r[1]).longValue();
      BigDecimal value = money(r[2]);
      BigDecimal discount = money(r[3]);
      BigDecimal liquid = money(r[4]);

      labels.add(label);
      primary.add(value);
      secondary.add(liquid);
      tableRows.add(new SalesRow(label, tx, value, discount, liquid, percentage(value, totalValue)));
    }

    return new SalesBlock(labels, primary, secondary, tableRows);
  }

  // ---------------------------------------------------------------------------
  // PAYMENTS (CreditOrder): vendido (primary) x recebido/liquidado (secondary)
  // ---------------------------------------------------------------------------
  private SalesBlock buildPayments(ManagementDashboardRequest req, Dimension dim) {
    String labelExpr = dim.labelExpr("c");
    String groupExpr = dim.groupExpr("c");

    StringBuilder jpql = new StringBuilder(
      "select " + labelExpr + " as lbl, " +
        "count(c.id), " +
        "coalesce(sum(c.releaseValue),0) " +
        "from CreditOrderEntity c "
    );
    // CreditOrder não tem modality própria; o filtro de modalidade não se aplica aqui.
    List<String> where = baseWhere("c", req, "c.releaseDate", false);
    appendWhere(jpql, where);
    jpql.append(" group by ").append(groupExpr).append(" order by ").append(labelExpr);

    Query q = entityManager.createQuery(jpql.toString());
    bindParams(q, req, false, true);

    List<Object[]> rows = q.getResultList();

    List<String> labels = new ArrayList<>();
    List<BigDecimal> primary = new ArrayList<>();
    List<BigDecimal> secondary = new ArrayList<>();
    List<SalesRow> tableRows = new ArrayList<>();

    BigDecimal totalValue = rows.stream()
      .map(r -> money(r[2]))
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    for (Object[] r : rows) {
      String label = label(r[0], dim);
      long tx = ((Number) r[1]).longValue();
      BigDecimal value = money(r[2]);
      // "recebido" = ordens já liquidadas no banco (releaseBank preenchido) — aproximado
      // pelo próprio releaseValue, pois o valor da ordem é o recebível. Sem desconto separado.
      BigDecimal liquid = value;

      labels.add(label);
      primary.add(value);
      secondary.add(liquid);
      tableRows.add(new SalesRow(label, tx, value, BigDecimal.ZERO.setScale(MONEY_SCALE), liquid, percentage(value, totalValue)));
    }

    return new SalesBlock(labels, primary, secondary, tableRows);
  }

  // ---------------------------------------------------------------------------
  // FEES (TransactionAcq): taxa efetiva por dimensão + média geral (benchmark)
  // ---------------------------------------------------------------------------
  private FeesBlock buildFees(ManagementDashboardRequest req, Dimension dim) {
    String labelExpr = dim.labelExpr("t");
    String groupExpr = dim.groupExpr("t");

    StringBuilder jpql = new StringBuilder(
      "select " + labelExpr + " as lbl, " +
        "count(t.id), " +
        "coalesce(sum(t.grossValue),0), " +
        "coalesce(sum(t.discountValue),0) " +
        "from TransactionAcqEntity t "
    );
    List<String> where = baseWhere("t", req, "t.saleDate", true);
    appendWhere(jpql, where);
    jpql.append(" group by ").append(groupExpr).append(" order by ").append(labelExpr);

    Query q = entityManager.createQuery(jpql.toString());
    bindParams(q, req, true, false);

    List<Object[]> rows = q.getResultList();

    BigDecimal totalGross = rows.stream().map(r -> money(r[2])).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalDiscount = rows.stream().map(r -> money(r[3])).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal averageRate = rate(totalDiscount, totalGross);

    List<String> labels = new ArrayList<>();
    List<BigDecimal> effective = new ArrayList<>();
    List<BigDecimal> average = new ArrayList<>();
    List<FeesRow> tableRows = new ArrayList<>();

    for (Object[] r : rows) {
      String label = label(r[0], dim);
      long tx = ((Number) r[1]).longValue();
      BigDecimal gross = money(r[2]);
      BigDecimal discount = money(r[3]);
      BigDecimal effRate = rate(discount, gross);

      labels.add(label);
      effective.add(effRate);
      average.add(averageRate); // benchmark geral, igual em todos os labels
      tableRows.add(new FeesRow(label, tx, effRate, discount, percentage(discount, totalDiscount)));
    }

    return new FeesBlock(labels, effective, average, tableRows);
  }

  // ---------------------------------------------------------------------------
  // DEBITS (Adjustment): séries por dimensão + cards de resumo por tipo
  // ---------------------------------------------------------------------------
  private DebitsBlock buildDebits(ManagementDashboardRequest req, Dimension dim) {
    DebitAggregate cancellation = debitAggregate(req, dim, CANCELLATION_REASONS);
    DebitAggregate fees = debitAggregate(req, dim, FEE_REASONS);
    DebitAggregate chargeback = debitAggregate(req, dim, CHARGEBACK_REASONS);

    // União ordenada de labels para alinhar as três séries.
    java.util.LinkedHashSet<String> labelSet = new java.util.LinkedHashSet<>();
    labelSet.addAll(cancellation.byLabel().keySet());
    labelSet.addAll(fees.byLabel().keySet());
    labelSet.addAll(chargeback.byLabel().keySet());
    List<String> labels = new ArrayList<>(labelSet);

    List<BigDecimal> cancellationSeries = new ArrayList<>();
    List<BigDecimal> feesSeries = new ArrayList<>();
    List<BigDecimal> chargebackSeries = new ArrayList<>();
    for (String label : labels) {
      cancellationSeries.add(cancellation.byLabel().getOrDefault(label, zeroMoney()));
      feesSeries.add(fees.byLabel().getOrDefault(label, zeroMoney()));
      chargebackSeries.add(chargeback.byLabel().getOrDefault(label, zeroMoney()));
    }

    return new DebitsBlock(
      labels,
      cancellationSeries,
      feesSeries,
      chargebackSeries,
      fees.summary(),
      chargeback.summary(),
      cancellation.summary()
    );
  }

  private record DebitAggregate(Map<String, BigDecimal> byLabel, DebitSummary summary) {}

  /**
   * Uma única query agregada por conjunto de motivos: retorna soma por rótulo e,
   * derivado dela, o resumo total (total/quantidade/média). Evita queries separadas
   * para série e resumo.
   */
  private DebitAggregate debitAggregate(ManagementDashboardRequest req, Dimension dim, List<Integer> reasons) {
    String labelExpr = dim.labelExpr("a");
    String groupExpr = dim.groupExpr("a");

    StringBuilder jpql = new StringBuilder(
      "select " + labelExpr + " as lbl, coalesce(sum(a.adjustmentValue),0), count(a.id) from AdjustmentEntity a "
    );
    List<String> where = baseWhereAdjustment(req);
    where.add("a.adjustmentReason in :reasons");
    appendWhere(jpql, where);
    jpql.append(" group by ").append(groupExpr).append(" order by ").append(labelExpr);

    Query q = entityManager.createQuery(jpql.toString());
    bindParamsAdjustment(q, req);
    q.setParameter("reasons", reasons);

    List<Object[]> rows = q.getResultList();

    Map<String, BigDecimal> byLabel = new LinkedHashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    long quantity = 0;
    for (Object[] r : rows) {
      BigDecimal value = money(r[1]);
      long count = ((Number) r[2]).longValue();
      byLabel.put(label(r[0], dim), value);
      total = total.add(value);
      quantity += count;
    }

    BigDecimal totalMoney = total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal average = quantity == 0 ? zeroMoney() : totalMoney.divide(BigDecimal.valueOf(quantity), MONEY_SCALE, RoundingMode.HALF_UP);
    return new DebitAggregate(byLabel, new DebitSummary(totalMoney, quantity, average));
  }

  // ---------------------------------------------------------------------------
  // WHERE helpers
  // ---------------------------------------------------------------------------
  private List<String> baseWhere(String alias, ManagementDashboardRequest req, String dateField, boolean applyModality) {
    List<String> where = new ArrayList<>();
    if (hasItems(req.companyIds())) {
      where.add(alias + ".company.id in :companyIds");
    }
    if (hasItems(req.acquirerIds())) {
      where.add(alias + ".acquirer.id in :acquirerIds");
    }
    if (applyModality && hasItems(req.modalities())) {
      where.add(alias + ".modality in :modalityCodes");
    }
    if (hasItems(req.flagIds())) {
      where.add(alias + ".flag.id in :flagIds");
    }
    DateRange range = resolveRange(req);
    if (range.start() != null) {
      where.add(dateField + " >= :startDate");
    }
    if (range.end() != null) {
      where.add(dateField + " <= :endDate");
    }
    return where;
  }

  private List<String> baseWhereAdjustment(ManagementDashboardRequest req) {
    List<String> where = new ArrayList<>();
    if (hasItems(req.companyIds())) {
      where.add("a.company.id in :companyIds");
    }
    if (hasItems(req.acquirerIds())) {
      where.add("a.acquirer.id in :acquirerIds");
    }
    if (hasItems(req.flagIds())) {
      where.add("a.rvFlagAdjustment.id in :flagIds");
    }
    DateRange range = resolveRange(req);
    if (range.start() != null) {
      where.add("a.adjustmentDate >= :startDate");
    }
    if (range.end() != null) {
      where.add("a.adjustmentDate <= :endDate");
    }
    return where;
  }

  private void appendWhere(StringBuilder jpql, List<String> where) {
    if (!where.isEmpty()) {
      jpql.append(" where ").append(String.join(" and ", where));
    }
  }

  private void bindParams(Query q, ManagementDashboardRequest req, boolean applyModality, boolean localDateColumn) {
    if (hasItems(req.companyIds())) {
      q.setParameter("companyIds", toUuids(req.companyIds()));
    }
    if (hasItems(req.acquirerIds())) {
      q.setParameter("acquirerIds", toUuids(req.acquirerIds()));
    }
    if (applyModality && hasItems(req.modalities())) {
      q.setParameter("modalityCodes", modalityCodes(req.modalities()));
    }
    if (hasItems(req.flagIds())) {
      q.setParameter("flagIds", toUuids(req.flagIds()));
    }
    DateRange range = resolveRange(req);
    if (range.start() != null) {
      q.setParameter("startDate", localDateColumn ? range.start() : startOfDay(range.start()));
    }
    if (range.end() != null) {
      q.setParameter("endDate", localDateColumn ? range.end() : endOfDay(range.end()));
    }
  }

  private void bindParamsAdjustment(Query q, ManagementDashboardRequest req) {
    if (hasItems(req.companyIds())) {
      q.setParameter("companyIds", toUuids(req.companyIds()));
    }
    if (hasItems(req.acquirerIds())) {
      q.setParameter("acquirerIds", toUuids(req.acquirerIds()));
    }
    if (hasItems(req.flagIds())) {
      q.setParameter("flagIds", toUuids(req.flagIds()));
    }
    DateRange range = resolveRange(req);
    if (range.start() != null) {
      q.setParameter("startDate", range.start());
    }
    if (range.end() != null) {
      q.setParameter("endDate", range.end());
    }
  }

  private OffsetDateTime startOfDay(LocalDate d) {
    return d.atStartOfDay().atOffset(ZoneOffset.UTC);
  }

  private OffsetDateTime endOfDay(LocalDate d) {
    return d.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
  }

  private record DateRange(LocalDate start, LocalDate end) {
    static DateRange empty() {
      return new DateRange(null, null);
    }
  }

  /**
   * Converte o filtro de período da venda (mesma semântica dos specs do sistema) em uma
   * faixa [start, end] de datas:
   * - DAY   : start = end = data informada
   * - START : a partir de (>= data)
   * - END   : até (<= data)
   * - MONTH : mês inteiro (YYYY-MM)
   * - YEAR  : ano inteiro (YYYY)
   * - INTERVAL: entre duas datas (dois valores)
   */
  private DateRange resolveRange(ManagementDashboardRequest req) {
    PeriodEnum period = req.periodSaleDate();
    List<String> values = req.saleDate();

    if (period == null || period == PeriodEnum.NULL || values == null || values.isEmpty()) {
      return DateRange.empty();
    }

    return switch (period) {
      case DAY -> {
        LocalDate d = parseDate(values.get(0));
        yield d == null ? DateRange.empty() : new DateRange(d, d);
      }
      case START -> {
        LocalDate d = parseDate(values.get(0));
        yield d == null ? DateRange.empty() : new DateRange(d, null);
      }
      case END -> {
        LocalDate d = parseDate(values.get(0));
        yield d == null ? DateRange.empty() : new DateRange(null, d);
      }
      case MONTH -> {
        YearMonth ym = parseMonth(values.get(0));
        yield ym == null ? DateRange.empty() : new DateRange(ym.atDay(1), ym.atEndOfMonth());
      }
      case YEAR -> {
        Year y = parseYear(values.get(0));
        yield y == null ? DateRange.empty() : new DateRange(y.atDay(1), y.atMonth(12).atEndOfMonth());
      }
      case INTERVAL -> {
        LocalDate start = parseDate(values.get(0));
        LocalDate end = values.size() > 1 ? parseDate(values.get(1)) : null;
        yield new DateRange(start, end);
      }
      case NULL -> DateRange.empty();
    };
  }

  private LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) return null;
    // Reutiliza o parser flexível do sistema: aceita dd/MM/yyyy, d/M/yyyy, ISO date e ISO datetime.
    OffsetDateTime parsed = dateFilterService.parseFlexibleToOffsetDateTime(value.trim());
    return parsed != null ? parsed.toLocalDate() : null;
  }

  private static final java.time.format.DateTimeFormatter BR_MONTH =
    java.time.format.DateTimeFormatter.ofPattern("MM/yyyy");

  private YearMonth parseMonth(String value) {
    if (value == null || value.isBlank()) return null;
    String v = value.trim();
    // Formato do front: "MM/yyyy" (ex.: "06/2026"). Tolera também ISO "yyyy-MM".
    try {
      return YearMonth.parse(v, BR_MONTH);
    } catch (RuntimeException ignored) {
      // tenta ISO yyyy-MM
    }
    try {
      return YearMonth.parse(v);
    } catch (RuntimeException ignored) {
      // último recurso: extrai o mês de uma data completa (dd/MM/yyyy ou ISO)
      LocalDate d = parseDate(v);
      return d == null ? null : YearMonth.from(d);
    }
  }

  private Year parseYear(String value) {
    if (value == null || value.isBlank()) return null;
    String v = value.trim();
    // Formato do front: "yyyy" (ex.: "2026").
    try {
      return Year.parse(v);
    } catch (RuntimeException ignored) {
      LocalDate d = parseDate(v);
      return d == null ? null : Year.from(d);
    }
  }

  private List<UUID> toUuids(List<String> ids) {
    return ids.stream().filter(s -> s != null && !s.isBlank()).map(UUID::fromString).toList();
  }

  /**
   * Mapeia as modalidades do contrato (CREDIT, DEBIT, PIX) para os códigos do ModalityEnum.
   * CREDIT = todas as modalidades de crédito; DEBIT = débito à vista; PIX = carteira digital.
   */
  private List<Integer> modalityCodes(List<String> modalities) {
    List<Integer> codes = new ArrayList<>();
    for (String m : modalities) {
      if (m == null) continue;
      switch (m.trim().toUpperCase()) {
        case "CREDIT" -> {
          codes.add(ModalityEnum.CASH_CREDIT.getCode());
          codes.add(ModalityEnum.INSTALLMENT_CREDIT_2_6.getCode());
          codes.add(ModalityEnum.INSTALLMENT_CREDIT_7_12.getCode());
          codes.add(ModalityEnum.INSTALLMENT_CREDIT_13_21.getCode());
        }
        case "DEBIT" -> codes.add(ModalityEnum.CASH_DEBIT.getCode());
        case "PIX" -> codes.add(ModalityEnum.DIGITAL_WALLET.getCode());
        default -> log.debug("Modalidade desconhecida no dashboard: {}", m);
      }
    }
    return codes.isEmpty() ? List.of(-1) : codes;
  }

  private boolean hasItems(List<?> list) {
    return list != null && !list.isEmpty();
  }

  private BigDecimal money(Object value) {
    if (value == null) return zeroMoney();
    return new BigDecimal(value.toString()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal zeroMoney() {
    return BigDecimal.ZERO.setScale(MONEY_SCALE);
  }

  private BigDecimal percentage(BigDecimal part, BigDecimal total) {
    if (total == null || total.signum() == 0) return BigDecimal.ZERO.setScale(RATE_SCALE);
    return part.multiply(HUNDRED).divide(total, RATE_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal rate(BigDecimal discount, BigDecimal gross) {
    if (gross == null || gross.signum() == 0) return BigDecimal.ZERO.setScale(RATE_SCALE);
    return discount.multiply(HUNDRED).divide(gross, RATE_SCALE, RoundingMode.HALF_UP);
  }

  private String label(Object raw, Dimension dim) {
    return dim.label(raw);
  }

  private String groupByOrDefault(ManagementDashboardRequest req, GroupSection section) {
    ManagementDashboardRequest.GroupBy g = req.groupBy();
    String value = null;
    if (g != null) {
      value = switch (section) {
        case SALES -> g.sales();
        case PAYMENTS -> g.payments();
        case FEES -> g.fees();
        case DEBITS -> g.debits();
      };
    }
    if (value != null && !value.isBlank()) {
      return value;
    }
    return section.defaultDimension;
  }

  private ManagementDashboardRequest emptyRequest() {
    return new ManagementDashboardRequest(List.of(), List.of(), List.of(), List.of(), null, List.of(), null);
  }

  private enum GroupSection {
    SALES("COMPANY"),
    PAYMENTS("ACQUIRER"),
    FEES("FLAG"),
    DEBITS("DATE");

    final String defaultDimension;

    GroupSection(String defaultDimension) {
      this.defaultDimension = defaultDimension;
    }
  }

  /**
   * Dimensão de agrupamento. Encapsula a expressão JPQL de agrupamento/rótulo e como
   * converter o valor cru retornado em rótulo de exibição.
   */
  private enum Dimension {
    COMPANY, ACQUIRER, MODALITY, FLAG, DATE;

    static Dimension resolve(String value) {
      if (value == null || value.isBlank()) return COMPANY;
      try {
        return Dimension.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException ex) {
        return COMPANY;
      }
    }

    /** Expressão usada no group by. */
    String groupExpr(String alias) {
      return switch (this) {
        case COMPANY -> alias + ".company.fantasyName";
        case ACQUIRER -> alias + ".acquirer.fantasyName";
        case MODALITY -> modalityExpr(alias);
        case FLAG -> flagExpr(alias);
        case DATE -> dateExpr(alias);
      };
    }

    private String modalityExpr(String alias) {
      // Apenas TransactionAcq (alias "t") possui modality. CreditOrder e Adjustment não;
      // nesses casos, agrupa por adquirente como fallback seguro.
      return "t".equals(alias) ? alias + ".modality" : alias + ".acquirer.fantasyName";
    }

    /** Expressão usada no select do rótulo (mesma do group by). */
    String labelExpr(String alias) {
      return groupExpr(alias);
    }

    private String flagExpr(String alias) {
      // Adjustment usa rvFlagAdjustment; demais entidades usam flag.
      return "a".equals(alias) ? alias + ".rvFlagAdjustment.name" : alias + ".flag.name";
    }

    private String dateExpr(String alias) {
      // Agrupa por ANO. Para Acq (OffsetDateTime) e CreditOrder/Adjustment (LocalDate),
      // function('year', ...) extrai o ano da data.
      String field = switch (alias) {
        case "t" -> alias + ".saleDate";
        case "c" -> alias + ".releaseDate";
        default -> alias + ".adjustmentDate";
      };
      return "function('year'," + field + ")";
    }

    String label(Object raw) {
      if (raw == null) return "—";
      if (this == MODALITY && raw instanceof Number number) {
        int code = number.intValue();
        try {
          ModalityEnum modality = ModalityEnum.fromCode(code);
          return modality != null ? modality.name() : String.valueOf(code);
        } catch (RuntimeException ex) {
          return String.valueOf(code);
        }
      }
      if (this == DATE && raw instanceof Number number) {
        // function('year', ...) retorna o ano como número; exibe como inteiro (ex.: "2026").
        return String.valueOf(number.intValue());
      }
      return String.valueOf(raw);
    }
  }
}