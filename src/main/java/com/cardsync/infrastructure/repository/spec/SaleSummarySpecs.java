package com.cardsync.infrastructure.repository.spec;

import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.filter.SaleSummaryFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.SaleSummaryAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import com.cardsync.infrastructure.repository.spec.tableFilters.SaleSummaryTableFields;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SaleSummarySpecs extends BaseSpecificationSupport<SalesSummaryEntity> {

  private final SpecificationFactory specificationFactory;
  private final SaleSummaryTableFields saleSummaryTableFields;
  private final ImplantationDateProvider implantationDateProvider;
  private final SaleSummaryAdvancedFields saleSummaryAdvancedFields;

  public SaleSummarySpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    SaleSummaryTableFields saleSummaryTableFields,
    ImplantationDateProvider implantationDateProvider,
    SaleSummaryAdvancedFields saleSummaryAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory = specificationFactory;
    this.saleSummaryTableFields = saleSummaryTableFields;
    this.implantationDateProvider = implantationDateProvider;
    this.saleSummaryAdvancedFields = saleSummaryAdvancedFields;
  }

  public Specification<SalesSummaryEntity> fromQuery(ListQueryDto<SaleSummaryFilter> query) {
    Specification<SalesSummaryEntity> spec = baseFilters(query)
      .and(fetchListAssociations());

    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<SalesSummaryEntity> fromQueryForTotals(ListQueryDto<SaleSummaryFilter> query) {
    return baseFilters(query);
  }

  public Specification<SalesSummaryEntity> fromQueryForPendingCreditOrders(
      ListQueryDto<SaleSummaryFilter> query, LocalDate cutoffDate, LocalDate yesterday) {
    Specification<SalesSummaryEntity> spec = baseFilters(query)
      .and(rvDateBefore(cutoffDate))
      .and(installmentModalitySpec())
      .and(missingCreditOrdersSpec())
      .and(nextReleaseDateNotFutureSpec(yesterday))
      .and(fetchListAssociations());
    return spec.and(orderByTableSort(query == null ? null : query.sort()));
  }

  public Specification<SalesSummaryEntity> fromQueryForPendingCreditOrdersTotals(
      ListQueryDto<SaleSummaryFilter> query, LocalDate cutoffDate, LocalDate yesterday) {
    return baseFilters(query)
      .and(rvDateBefore(cutoffDate))
      .and(installmentModalitySpec())
      .and(missingCreditOrdersSpec())
      .and(nextReleaseDateNotFutureSpec(yesterday));
  }

  private static Specification<SalesSummaryEntity> rvDateBefore(LocalDate date) {
    return (root, query, cb) -> cb.lessThan(root.<LocalDate>get("rvDate"), date);
  }

  private static Specification<SalesSummaryEntity> installmentModalitySpec() {
    return (root, query, cb) -> root.get("modality").in(
      ModalityEnum.CASH_CREDIT.getCode(),
      ModalityEnum.INSTALLMENT_CREDIT_2_6.getCode(),
      ModalityEnum.INSTALLMENT_CREDIT_7_12.getCode(),
      ModalityEnum.INSTALLMENT_CREDIT_13_21.getCode()
    );
  }

  private static Specification<SalesSummaryEntity> missingCreditOrdersSpec() {
    return (root, query, cb) -> {
      // countDistinct(installmentNumber), não count(id): CreditOrder duplicada para a mesma
      // parcela (ex.: reenvio de arquivo EEFI da adquirente cobrindo a mesma parcela já
      // importada — não há constraint único nem checagem de idempotência por parcela) não
      // pode mascarar uma parcela realmente faltante como "já coberta".
      Subquery<Long> countSq = query.subquery(Long.class);
      Root<CreditOrderEntity> coRoot = countSq.from(CreditOrderEntity.class);
      countSq.select(cb.countDistinct(coRoot.get("installmentNumber")))
             .where(cb.equal(coRoot.get("salesSummary"), root));

      Subquery<Long> maxSq = query.subquery(Long.class);
      Root<CreditOrderEntity> co2Root = maxSq.from(CreditOrderEntity.class);
      maxSq.select(cb.max(co2Root.<Integer>get("installmentTotal")).as(Long.class))
           .where(cb.equal(co2Root.get("salesSummary"), root));

      CriteriaBuilder.Coalesce<Long> coalesce = cb.coalesce();
      coalesce.value(maxSq);
      coalesce.value(1L);

      return cb.lt(countSq, coalesce);
    };
  }

  /**
   * Garante que exista pelo menos uma parcela faltante com vencimento <= ontem — impede gerar
   * ordens com vencimento futuro que ainda podem vir no arquivo da adquirente.
   *
   * Sem ordens existentes: baseDate (parcela 1) <= yesterday.
   * Com ordens existentes: existe uma parcela "logo depois" de uma já criada (installmentNumber+1)
   * que ainda não existe e já venceu (ver {@link #gapAfterExistingDueSpec}) — NÃO usa mais
   * MAX(releaseDate) das existentes, porque isso escondia lacunas quando uma parcela era criada
   * fora de ordem (ex.: RV 56649219 tinha as parcelas 1 e 3, faltando a 2 já vencida; MAX(releaseDate)
   * vinha da parcela 3 — mais recente — e mascarava a lacuna real da parcela 2).
   */
  private static Specification<SalesSummaryEntity> nextReleaseDateNotFutureSpec(LocalDate yesterday) {
    return (root, query, cb) -> {
      // Subquery: contagem de ordens existentes
      Subquery<Long> existsCountSq = query.subquery(Long.class);
      Root<CreditOrderEntity> coEx = existsCountSq.from(CreditOrderEntity.class);
      existsCountSq.select(cb.count(coEx.get("id")))
                   .where(cb.equal(coEx.get("salesSummary"), root));

      // baseDate = coalesce(firstInstallmentCreditDate, rvDate)
      CriteriaBuilder.Coalesce<LocalDate> baseDate = cb.coalesce();
      baseDate.value(root.get("firstInstallmentCreditDate"));
      baseDate.value(root.get("rvDate"));

      // Condição A: sem ordens — parcela 1 (baseDate) <= yesterday
      Predicate noOrders  = cb.equal(existsCountSq, 0L);
      Predicate baseDateOk = cb.lessThanOrEqualTo(baseDate, yesterday);

      // Condição B: com ordens — existe uma lacuna já vencida logo após alguma parcela existente
      Predicate hasOrders = cb.greaterThan(existsCountSq, 0L);
      Predicate gapDueOk  = gapAfterExistingDueSpec(root, query, cb, baseDate, yesterday);

      return cb.or(
        cb.and(noOrders,  baseDateOk),
        cb.and(hasOrders, gapDueOk)
      );
    };
  }

  /** Meses de parcelamento considerados ao montar a condição "já venceu" abaixo (3 anos cobre folgadamente os planos reais vistos na base — até 10 parcelas). */
  private static final int MAX_INSTALLMENTS_CONSIDERED = 36;

  /**
   * Existe alguma parcela existente cujo installmentNumber+1 (a) ainda não tem ordem de crédito,
   * (b) está dentro do total de parcelas do resumo, e (c) seu vencimento (baseDate + installmentNumber
   * meses) já passou.
   *
   * <p>Monta "baseDate + k meses <= yesterday" como "baseDate <= (yesterday - k meses)" pra cada
   * k possível, calculado em Java e comparado como uma data literal (mesmo padrão já usado em
   * baseDateOk, sem função de data alguma) — evita repetir o erro só corrigido em
   * HolidayRepository#findActiveByDate ("date_part(unknown, unknown) não é única": Postgres não
   * consegue resolver a sobrecarga de date_part/age quando o argumento chega sem tipo concreto).
   */
  private static Predicate gapAfterExistingDueSpec(
      Root<SalesSummaryEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb,
      Expression<LocalDate> baseDate, LocalDate yesterday) {

    Subquery<Integer> gapSq = query.subquery(Integer.class);
    Root<CreditOrderEntity> gapRoot = gapSq.from(CreditOrderEntity.class);
    Expression<Integer> gapInstallmentNumber = gapRoot.get("installmentNumber");

    Subquery<Integer> nextExistsSq = gapSq.subquery(Integer.class);
    Root<CreditOrderEntity> nextRoot = nextExistsSq.from(CreditOrderEntity.class);
    nextExistsSq.select(nextRoot.get("installmentNumber"))
                .where(
                  cb.equal(nextRoot.get("salesSummary"), root),
                  cb.equal(nextRoot.<Integer>get("installmentNumber"), cb.sum(gapInstallmentNumber, 1))
                );

    List<Predicate> dueForInstallmentNumber = new ArrayList<>();
    for (int k = 1; k <= MAX_INSTALLMENTS_CONSIDERED; k++) {
      dueForInstallmentNumber.add(cb.and(
        cb.equal(gapInstallmentNumber, k),
        cb.lessThanOrEqualTo(baseDate, yesterday.minusMonths(k))
      ));
    }

    gapSq.select(gapInstallmentNumber)
         .where(
           cb.equal(gapRoot.get("salesSummary"), root),
           cb.not(cb.exists(nextExistsSq)),
           cb.le(cb.sum(gapInstallmentNumber, 1), gapRoot.<Integer>get("installmentTotal")),
           cb.or(dueForInstallmentNumber.toArray(new Predicate[0]))
         );

    return cb.exists(gapSq);
  }

  private Specification<SalesSummaryEntity> baseFilters(ListQueryDto<SaleSummaryFilter> query) {
    Specification<SalesSummaryEntity> spec = Specs.all();

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          saleSummaryTableFields.table()
        )
      );

      spec = spec.and(saleSummaryAdvancedFields.advanced(query.advanced()));
    }

    spec = spec.and(dateGreaterThanOrEqual("rvDate", implantationDateProvider.get(), false));
    return spec;
  }

  private Specification<SalesSummaryEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "flag");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "processedFile");

        var bankingDomicile = fetchIfNotFetched(root, "bankingDomicile");
        fetchIfNotFetched(bankingDomicile, "bank");

        // Sem distinct(true) aqui de propósito: todas as associações buscadas acima são
        // @ManyToOne (flag/company/acquirer/processedFile/bankingDomicile/bank) — nenhuma
        // multiplica linhas, então DISTINCT nunca foi necessário pra elas. Mantê-lo quebrava
        // nextInstallmentValue/nextInstallmentDate (ordenação por subquery correlacionada): o
        // Postgres exige que toda expressão do ORDER BY apareça na lista de seleção quando a
        // query é DISTINCT ("para SELECT DISTINCT, expressões ORDER BY devem aparecer na lista
        // de seleção"), e essas expressões calculadas não fazem parte do SELECT (que é só a
        // entidade). A única associação a-muitos do resumo (adjustments) não é buscada aqui — só
        // é referenciada pelo alias de ordenação "adjustmentValue", que já está quebrado por outro
        // motivo (usa o nome errado "adjustment", singular, em vez de "adjustments").
      }

      return cb.conjunction();
    };
  }

  private Specification<SalesSummaryEntity> orderByTableSort(List<SortDto> sort) {
    Specification<SalesSummaryEntity> sortSpec = tableSort(sort, "pvNumber", Map.of(
      "flag",                 sortJoin("flag", "name"),
      "conciliationDate",     sortField("saleReconciliationDate"),
      "company",              sortJoin("company", "fantasyName"),
      "acquirer",             sortJoin("acquirer", "fantasyName"),
      "adjustmentValue",      sortJoin("adjustment", "adjustmentValue"),
      "nextInstallmentValue", (root, query, cb, desc) -> nextInstallmentValueSort(root, query, cb),
      "nextInstallmentDate",  (root, query, cb, desc) -> nextInstallmentDateSort(root, query, cb)
    ));

    // tableSort() (BaseSpecificationSupport, compartilhado por várias *Specs do sistema) sempre
    // força query.distinct(true) — necessário em telas com join pra associação a-muitos, mas
    // aqui não há nenhuma (ver fetchListAssociations()). Com DISTINCT, o Postgres exige que toda
    // expressão do ORDER BY apareça na lista de seleção; nextInstallmentValueSort/
    // nextInstallmentDateSort são subqueries correlacionadas que não fazem parte do SELECT
    // (que é só a entidade), então DISTINCT quebra a ordenação por elas ("para SELECT DISTINCT,
    // expressões ORDER BY devem aparecer na lista de seleção"). Desliga de volta aqui, sem mexer
    // no comportamento padrão de tableSort() usado pelas outras *Specs.
    return (root, query, cb) -> {
      Predicate predicate = sortSpec.toPredicate(root, query, cb);
      if (!isCountQuery(query)) {
        query.distinct(false);
      }
      return predicate;
    };
  }

  /**
   * Ordena pela mesma prévia de valor exibida na tela (ver
   * CreditOrderManualService#computeInstallmentValue): liquidValue / installmentTotal, onde
   * installmentTotal = MAX(TransactionAcqEntity.installment) correlacionado por salesSummary
   * (mesmo dado buscado em lote por TransactionAcqRepository#findMaxInstallmentBySalesSummaryIdIn
   * pra exibição, aqui recalculado por linha via subquery — padrão análogo a
   * missingCreditOrdersSpec()). Ordena pelo valor sem truncar a 2 casas — truncamento é
   * monotônico, então a ordem relativa é idêntica à do valor exibido; empates de centavo já são
   * resolvidos pelo desempate por id em tableSort().
   */
  private Expression<? extends Number> nextInstallmentValueSort(
      Root<SalesSummaryEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    Subquery<Integer> maxInstallmentSq = query.subquery(Integer.class);
    Root<TransactionAcqEntity> taRoot = maxInstallmentSq.from(TransactionAcqEntity.class);
    maxInstallmentSq.select(cb.max(taRoot.<Integer>get("installment")))
                    .where(cb.equal(taRoot.get("salesSummary"), root));

    CriteriaBuilder.Coalesce<Integer> installmentTotal = cb.coalesce();
    installmentTotal.value(maxInstallmentSq);
    installmentTotal.value(1);

    return cb.quot(root.<BigDecimal>get("liquidValue"), installmentTotal);
  }

  /**
   * Ordena por uma aproximação da data de vencimento da próxima ordem de crédito (ver
   * CreditOrderManualService#fillNextInstallmentPreview): índice ano*12+mês de baseDate, somado
   * à contagem de ordens já existentes (proxy pro número da próxima parcela — não busca o menor
   * número faltante exato, que exigiria CASE/EXISTS por parcela; para ordenação, a diferença só
   * importa nos raros casos de parcela criada fora de ordem) e à fração do dia do mês (desempate
   * fino). NÃO usa age()/date_part sobre parâmetro algum — só sobre baseDate (coalesce de duas
   * colunas reais), validado empiricamente contra a base antes de codificar; evita repetir o erro
   * corrigido em HolidayRepository#findActiveByDate ("date_part(unknown, unknown) não é única").
   */
  private Expression<? extends Number> nextInstallmentDateSort(
      Root<SalesSummaryEntity> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
    CriteriaBuilder.Coalesce<LocalDate> baseDate = cb.coalesce();
    baseDate.value(root.get("firstInstallmentCreditDate"));
    baseDate.value(root.get("rvDate"));

    Expression<Double> yearOfBase = cb.function("date_part", Double.class, cb.literal("year"), baseDate);
    Expression<Double> monthOfBase = cb.function("date_part", Double.class, cb.literal("month"), baseDate);
    Expression<Double> dayOfBase = cb.function("date_part", Double.class, cb.literal("day"), baseDate);

    Subquery<Long> existingCountSq = query.subquery(Long.class);
    Root<CreditOrderEntity> coRoot = existingCountSq.from(CreditOrderEntity.class);
    existingCountSq.select(cb.count(coRoot.get("id")))
                   .where(cb.equal(coRoot.get("salesSummary"), root));

    Expression<? extends Number> yearMonthIndex = cb.sum(cb.prod(yearOfBase, 12.0), monthOfBase);
    Expression<? extends Number> withInstallmentOffset = cb.sum(yearMonthIndex, existingCountSq.as(Double.class));
    Expression<? extends Number> dayFraction = cb.quot(dayOfBase, 31.0);
    return cb.sum(withInstallmentOffset, dayFraction);
  }
}
