package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.AnticipationModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.AnticipationModel;
import com.cardsync.domain.filter.AnticipationFilter;
import com.nimbussystems.commons.legacy.filter.query.ListQueryDto;
import com.cardsync.domain.model.AnticipationEntity;
import com.cardsync.domain.repository.AnticipationRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.AnticipationSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

    // Pageable só de page/size (sem sort): dataSpec já monta o ORDER BY completo via
    // orderByTableSort/tableSort (com os aliases de colunas ligadas por join, ex. "transactionsStatus"
    // -> salesSummary.transactionsStatus). SimpleJpaRepository#findAll(Specification, Pageable)
    // reaplica pageable.getSort() por cima, resolvendo o nome bruto direto contra AnticipationEntity
    // (sem conhecer os aliases) — sort por qualquer coluna que não seja campo direto da entidade
    // (ex.: "transactionsStatus", que só existe via salesSummary) quebra com "No property 'X' found
    // for type 'AnticipationEntity'" (achado real 2026-09-10, mesmo padrão já resolvido em
    // CreditOrderService). O pageable original (com sort) continua sendo usado só pro metadado da
    // resposta (PageImpl abaixo).
    Pageable pageableWithoutSort = pageable.isPaged()
      ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
      : Pageable.unpaged();

    List<AnticipationModel> content = total == 0
      ? List.of()
      : anticipationRepository.findAll(dataSpec, pageableWithoutSort)
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
      "discountRateValue",
      "releaseValue"
    );
  }
}
