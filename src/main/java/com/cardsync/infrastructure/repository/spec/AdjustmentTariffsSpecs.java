package com.cardsync.infrastructure.repository.spec;

import com.cardsync.domain.filter.AdjustmentFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.query.SortDto;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.enums.AdjustmentReasonEnum;
import com.cardsync.infrastructure.repository.spec.advancedFilters.AdjustmentAdvancedFields;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.SpecificationFactory;
import com.cardsync.infrastructure.repository.spec.tableFilters.AdjustmentTableFields;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AdjustmentTariffsSpecs extends BaseSpecificationSupport<AdjustmentEntity> {

  /**
   * Códigos de motivo que identificam ajustes de tarifa bancária.
   * Este filtro é fixo — a tela de tarifas nunca exibe outros tipos de ajuste.
   */
  private static final List<Integer> TARIFF_REASONS = List.of(
    AdjustmentReasonEnum.TX_MAN_TEF.getCode(),           // Tarifa manual TEF
    AdjustmentReasonEnum.TARIFA_CBK.getCode(),           // Tarifa chargeback
    AdjustmentReasonEnum.NAO_TOKENIZADAS.getCode(),      // Não tokenizadas
    AdjustmentReasonEnum.SALES_ANTICIPATION.getCode(),   // Antecipação de vendas
    AdjustmentReasonEnum.POS_INATIV_CONEC_PIN.getCode(), // POS Inativo/Conectividade/PINPAD
    AdjustmentReasonEnum.TRF_AD_EXCESSO_CBACK.getCode(), // Tarifa adicional excesso chargeback
    AdjustmentReasonEnum.AL_POS_PINPAD_TX_CONECT.getCode() // Aluguel POS/PINPAD/Taxa conectividade
  );

  private final SpecificationFactory specificationFactory;
  private final AdjustmentTableFields adjustmentTableFields;
  private final AdjustmentAdvancedFields adjustmentAdvancedFields;

  public AdjustmentTariffsSpecs(
    DateFilterService dateFilterService,
    SpecificationFactory specificationFactory,
    AdjustmentTableFields adjustmentTableFields,
    AdjustmentAdvancedFields adjustmentAdvancedFields
  ) {
    super(dateFilterService);
    this.specificationFactory    = specificationFactory;
    this.adjustmentTableFields   = adjustmentTableFields;
    this.adjustmentAdvancedFields = adjustmentAdvancedFields;
  }

  /** Spec completa: filtros + fetch joins + ordenação. Usada na query de dados. */
  public Specification<AdjustmentEntity> fromQuery(ListQueryDto<AdjustmentFilter> query) {
    return baseFilters(query)
      .and(fetchListAssociations())
      .and(orderByTableSort(query == null ? null : query.sort()));
  }

  /** Spec somente com filtros, sem fetch joins. Usada na query de COUNT. */
  public Specification<AdjustmentEntity> fromQueryForTotals(ListQueryDto<AdjustmentFilter> query) {
    return baseFilters(query);
  }

  // ─── filtros base ──────────────────────────────────────────────────────────

  private Specification<AdjustmentEntity> baseFilters(ListQueryDto<AdjustmentFilter> query) {
    // Filtro fixo: apenas ajustes de tarifa bancária, independente dos filtros do usuário.
    Specification<AdjustmentEntity> spec = (root, q, cb) ->
      root.get("adjustmentReason").in(TARIFF_REASONS);

    if (query != null) {
      spec = spec.and(
        specificationFactory.fromTableFilters(
          query.tableFilters(),
          adjustmentTableFields.table()
        )
      );

      spec = spec.and(adjustmentAdvancedFields.advanced(query.advanced()));

      if (!isBlank(query.globalFilter())) {
        String gf = query.globalFilter();
        spec = spec.and(anyOf(
          nsuGlobalFilter(gf, "nsu"),
          startsWith(gf, "authorization")
        ));
      }
    }

    return spec;
  }

  // ─── fetch joins (apenas na query de dados, nunca no COUNT) ────────────────

  private Specification<AdjustmentEntity> fetchListAssociations() {
    return (root, query, cb) -> {
      if (!isCountQuery(query)) {
        fetchIfNotFetched(root, "rvFlagAdjustment");
        fetchIfNotFetched(root, "company");
        fetchIfNotFetched(root, "acquirer");
        fetchIfNotFetched(root, "establishment");

        query.distinct(true);
      }
      return cb.conjunction();
    };
  }

  // ─── ordenação declarativa ─────────────────────────────────────────────────

  private Specification<AdjustmentEntity> orderByTableSort(List<SortDto> sort) {
    return tableSort(sort, "adjustmentDate", Map.of(
      "flag",        sortJoin("rvFlagAdjustment", "name"),
      "company",     sortJoin("company", "fantasyName"),
      "acquirer",    sortJoin("acquirer", "fantasyName"),
      "establishment", sortJoin("establishment", "pvNumber")
    ));
  }
}