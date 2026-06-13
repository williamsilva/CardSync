package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.AnticipationModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.AnticipationModel;
import com.cardsync.domain.filter.AnticipationFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.domain.repository.AnticipationRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.AnticipationSpecs;
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
public class AnticipationService {

  private final AnticipationSpecs anticipationSpecs;
  private final AnticipationRepository anticipationRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final AnticipationModelAssembler transactionsAcqModelAssembler;

  @Transactional(readOnly = true)
  public Page<AnticipationModel> search(Pageable pageable, ListQueryDto<AnticipationFilter> query) {
    Specification<AnticipationEntity> filterSpec = anticipationSpecs.fromQueryForTotals(query);
    Specification<AnticipationEntity> dataSpec   = anticipationSpecs.fromQuery(query);

    long total = anticipationRepository.count(filterSpec);

    List<AnticipationModel> content = total == 0
      ? List.of()
      : anticipationRepository.findAll(dataSpec, pageable)
      .stream()
      .map(transactionsAcqModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<AnticipationFilter> query) {
    Specification<AnticipationEntity> spec = anticipationSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      AnticipationEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }
}
