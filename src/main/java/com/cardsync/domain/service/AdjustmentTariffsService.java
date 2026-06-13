package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.AdjustmentTariffsModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentTariffsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentTotalsModel;
import com.cardsync.domain.filter.AdjustmentFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.service.support.AdjustmentTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.AdjustmentTariffsSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdjustmentTariffsService {

  private final AdjustmentTariffsSpecs adjustmentSpecs;
  private final AdjustmentRepository adjustmentRepository;
  private final AdjustmentTotalsQueryService totalsQueryService;
  private final AdjustmentTariffsModelAssembler adjustmentModelAssembler;

  /**
   * Busca paginada de ajustes/tarifas bancárias.
   *
   * <p>COUNT usa spec sem fetch joins — evita COUNT(DISTINCT id) com JOINs desnecessários.
   * DATA usa spec com fetch joins — carrega empresa, adquirente, bandeira e estabelecimento
   * em uma única query com DISTINCT.
   */
  @Transactional(readOnly = true)
  public Page<AdjustmentTariffsModel> search(Pageable pageable, ListQueryDto<AdjustmentFilter> query) {
    Specification<AdjustmentEntity> filterSpec = adjustmentSpecs.fromQueryForTotals(query);
    Specification<AdjustmentEntity> dataSpec   = adjustmentSpecs.fromQuery(query);

    long total = adjustmentRepository.count(filterSpec);

    List<AdjustmentTariffsModel> content = total == 0
      ? List.of()
      : adjustmentRepository.findAll(dataSpec, pageable)
      .stream()
      .map(adjustmentModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public AdjustmentTotalsModel totals(ListQueryDto<AdjustmentFilter> query) {
    Specification<AdjustmentEntity> spec = adjustmentSpecs.fromQueryForTotals(query);
    return totalsQueryService.totals(AdjustmentEntity.class, spec,"adjustmentValue");
  }
}