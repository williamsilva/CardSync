package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.CreditOrderModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.CreditOrderModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.filter.CreditOrderFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.CreditOrderSpecs;
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
public class CreditOrderService {

  private final CreditOrderSpecs creditOrderSpecs;
  private final CreditOrderRepository creditOrderRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final CreditOrderModelAssembler transactionsAcqModelAssembler;

  @Transactional(readOnly = true)
  public Page<CreditOrderModel> search(Pageable pageable, ListQueryDto<CreditOrderFilter> query) {
    Specification<CreditOrderEntity> filterSpec = creditOrderSpecs.fromQueryForTotals(query);
    Specification<CreditOrderEntity> dataSpec   = creditOrderSpecs.fromQuery(query);

    long total = creditOrderRepository.count(filterSpec);

    // Pageable só de page/size (sem sort): dataSpec já monta o ORDER BY completo via
    // orderByTableSort/tableSort (com os aliases de colunas ligadas por join, ex. "bank" ->
    // bankingDomicile.bank.name). SimpleJpaRepository#findAll(Specification, Pageable) reaplica
    // pageable.getSort() por cima, resolvendo o nome bruto direto contra CreditOrderEntity (sem
    // conhecer os aliases) — sort por qualquer coluna que não seja campo direto da entidade
    // (ex.: "bank", que só existe via bankingDomicile.bank) quebra com "No property 'X' found for
    // type 'CreditOrderEntity'". O pageable original (com sort) continua sendo usado só pro
    // metadado da resposta (PageImpl abaixo).
    Pageable pageableWithoutSort = pageable.isPaged()
      ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
      : Pageable.unpaged();

    List<CreditOrderModel> content = total == 0
      ? List.of()
      : creditOrderRepository.findAll(dataSpec, pageableWithoutSort)
      .stream()
      .map(transactionsAcqModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<CreditOrderFilter> query) {
    Specification<CreditOrderEntity> spec = creditOrderSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      CreditOrderEntity.class,
      spec,
      "grossRvValue",
      "discountRateValue",
      "releaseValue"
    );
  }
}
