package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.AdjustmentChargeBackRequestsModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentChargeBackRequestsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentTotalsModel;
import com.cardsync.domain.filter.AdjustmentChargeBackRequestsFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.RequestNoticeEntity;
import com.cardsync.domain.repository.RequestNoticeRepository;
import com.cardsync.domain.service.support.AdjustmentTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.AdjustmentChargeBackRequestsSpecs;
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
public class AdjustmentChargeBackRequestsService {

  private final RequestNoticeRepository adjustmentRepository;
  private final AdjustmentTotalsQueryService totalsQueryService;
  private final AdjustmentChargeBackRequestsSpecs adjustmentSpecs;
  private final AdjustmentChargeBackRequestsModelAssembler adjustmentModelAssembler;

  /**
   * Busca paginada de ajustes/tarifas bancárias.
   *
   * <p>COUNT usa spec sem fetch joins — evita COUNT(DISTINCT id) com JOINs desnecessários.
   * DATA usa spec com fetch joins — carrega empresa, adquirente, bandeira e estabelecimento
   * em uma única query com DISTINCT.
   */
  @Transactional(readOnly = true)
  public Page<AdjustmentChargeBackRequestsModel> search(Pageable pageable, ListQueryDto<AdjustmentChargeBackRequestsFilter> query) {
    Specification<RequestNoticeEntity> filterSpec = adjustmentSpecs.fromQueryForTotals(query);
    Specification<RequestNoticeEntity> dataSpec   = adjustmentSpecs.fromQuery(query);

    long total = adjustmentRepository.count(filterSpec);

    List<AdjustmentChargeBackRequestsModel> content = total == 0
      ? List.of()
      : adjustmentRepository.findAll(dataSpec, pageable)
      .stream()
      .map(adjustmentModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public AdjustmentTotalsModel totals(ListQueryDto<AdjustmentChargeBackRequestsFilter> query) {
    Specification<RequestNoticeEntity> spec = adjustmentSpecs.fromQueryForTotals(query);
    return totalsQueryService.totals(RequestNoticeEntity.class, spec,"adjustmentValue");
  }
}