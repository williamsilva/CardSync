package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.AdjustmentCancellationModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentCancellationModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.ValueTotalsModel;
import com.cardsync.domain.filter.AdjustmentFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.service.support.ValueTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.AdjustmentCancellationSpecs;
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
public class AdjustmentCancellationService {

  private final AdjustmentCancellationSpecs adjustmentSpecs;
  private final AdjustmentRepository adjustmentRepository;
  private final ValueTotalsQueryService totalsQueryService;
  private final AdjustmentCancellationModelAssembler adjustmentModelAssembler;

  /**
   * Busca paginada de ajustes/tarifas bancárias.
   *
   * <p>COUNT usa spec sem fetch joins — evita COUNT(DISTINCT id) com JOINs desnecessários.
   * DATA usa spec com fetch joins — carrega empresa, adquirente, bandeira e estabelecimento
   * em uma única query com DISTINCT.
   */
  @Transactional(readOnly = true)
  public Page<AdjustmentCancellationModel> search(Pageable pageable, ListQueryDto<AdjustmentFilter> query) {
    Specification<AdjustmentEntity> filterSpec = adjustmentSpecs.fromQueryForTotals(query);
    Specification<AdjustmentEntity> dataSpec   = adjustmentSpecs.fromQuery(query);

    long total = adjustmentRepository.count(filterSpec);

    List<AdjustmentCancellationModel> content = total == 0
      ? List.of()
      : adjustmentRepository.findAll(dataSpec, pageable)
      .stream()
      .map(adjustmentModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public ValueTotalsModel totals(ListQueryDto<AdjustmentFilter> query) {
    Specification<AdjustmentEntity> spec = adjustmentSpecs.fromQueryForTotals(query);
    return totalsQueryService.totals(AdjustmentEntity.class, spec,"adjustmentValue");
  }
}