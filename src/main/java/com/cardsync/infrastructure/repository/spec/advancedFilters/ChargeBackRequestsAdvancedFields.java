package com.cardsync.infrastructure.repository.spec.advancedFilters;

import com.cardsync.domain.filter.AdjustmentChargeBackRequestsFilter;
import com.cardsync.domain.model.RequestNoticeEntity;
import com.cardsync.domain.model.enums.ChargebackRequestReasonEnum;
import com.cardsync.domain.model.enums.ChargebackRequestStatusEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.infrastructure.repository.spec.config.BaseSpecificationSupport;
import com.cardsync.infrastructure.repository.spec.config.DateFilterService;
import com.cardsync.infrastructure.repository.spec.config.Specs;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Substitui uma versão anterior que era uma cópia colada de AdjustmentAdvancedFields (motivo de
 * ajuste bancário, faixas de adjustmentValue/grossValue/liquidValue, rvFlagAdjustment...) —
 * nenhum desses campos existe em RequestNoticeEntity, só em AdjustmentEntity. Essa versão usa os
 * campos reais da solicitação de chargeback (ver chargeback-request.filters.ts).
 */
@Component
public class ChargeBackRequestsAdvancedFields extends BaseSpecificationSupport<RequestNoticeEntity> {

  public ChargeBackRequestsAdvancedFields(DateFilterService dateFilterService) {
    super(dateFilterService);
  }

  public Specification<RequestNoticeEntity> advanced(AdjustmentChargeBackRequestsFilter filter) {
    if (filter == null) {
      return Specs.all();
    }

    Specification<RequestNoticeEntity> spec = Specs.all();

    spec = spec.and(numberFilter(filter.cvNsu(), "nsu"));
    spec = spec.and(numberFilter(filter.rvNumber(), "rvNumber"));
    spec = spec.and(startsWith(filter.authorization(), "authorization"));
    spec = spec.and(startsWith(filter.cardNumber(), "cardNumber"));

    spec = spec.and(inPath(filter.flags(), BaseSpecificationSupport::parseUuidOrNull, "flag", "id"));
    spec = spec.and(inPath(filter.companies(), BaseSpecificationSupport::parseUuidOrNull, "company", "id"));
    spec = spec.and(inPath(filter.acquirers(), BaseSpecificationSupport::parseUuidOrNull, "acquirer", "id"));
    spec = spec.and(inPath(filter.establishments(), BaseSpecificationSupport::parseUuidOrNull, "establishment", "id"));

    // Modalidade não é coluna própria — vem do resumo de vendas vinculado.
    spec = spec.and(inPath(filter.modality(), ModalityEnum::getCode, "salesSummary", "modality"));

    spec = spec.and(inCodes("requestStatus", filter.adjustmentStatus(), ChargebackRequestStatusEnum::getCode));
    spec = spec.and(requestReasonFilter(filter.requestReason()));

    spec = spec.and(localDatePeriod("saleDate", filter.periodSaleDate(), filter.saleDate(), true));
    spec = spec.and(localDatePeriod("deadline", filter.periodDeadline(), filter.deadline(), true));

    return spec;
  }

  /**
   * Cada motivo do painel é um bucket que agrupa vários request_code brutos legados enviados
   * pela adquirente (ver ChargebackRequestReasonEnum) — expande antes do IN, já que o código
   * real gravado quase nunca é o código-base 1-9 (ex.: dados reais usam 3001/5001/3034/3053).
   */
  private Specification<RequestNoticeEntity> requestReasonFilter(List<ChargebackRequestReasonEnum> reasons) {
    if (reasons == null || reasons.isEmpty()) {
      return alwaysTrue();
    }

    Set<Integer> codes = reasons.stream()
      .filter(Objects::nonNull)
      .flatMap(reason -> reason.getCodes().stream())
      .collect(Collectors.toCollection(LinkedHashSet::new));

    if (codes.isEmpty()) {
      return alwaysTrue();
    }

    return (root, query, cb) -> root.get("requestCode").in(codes);
  }
}
