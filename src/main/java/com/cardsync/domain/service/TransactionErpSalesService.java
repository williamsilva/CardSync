package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.TransactionsErpModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsErpModel;
import com.cardsync.domain.filter.TransactionErpSalesFilter;
import com.nimbussystems.commons.legacy.filter.query.ListQueryDto;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.repository.TransactionErpRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.TransactionErpSpecs;
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
public class TransactionErpSalesService {

  private final TransactionErpSpecs transactionErpSpecs;
  private final TransactionErpRepository transactionErpRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final TransactionsErpModelAssembler transactionsErpModelAssembler;

  @Transactional(readOnly = true)
  public Page<TransactionsErpModel> search(Pageable pageable, ListQueryDto<TransactionErpSalesFilter> query) {
    Specification<TransactionErpEntity> filterSpec = transactionErpSpecs.fromQueryForTotals(query);
    Specification<TransactionErpEntity> dataSpec   = transactionErpSpecs.fromQuery(query);

    long total = transactionErpRepository.count(filterSpec);

    // Pageable só de page/size (sem sort): dataSpec já monta o ORDER BY completo via
    // orderByTableSort/tableSort (com os aliases de colunas ligadas por join).
    // SimpleJpaRepository#findAll(Specification, Pageable) reaplica pageable.getSort() por
    // cima, resolvendo o nome bruto direto contra TransactionErpEntity (sem conhecer os
    // aliases) — sort por qualquer coluna que não seja campo direto da entidade quebra com "No
    // property 'X' found for type 'TransactionErpEntity'" (mesmo padrão de CreditOrderService/
    // AnticipationService, achado real 2026-09-10). O pageable original (com sort) continua
    // sendo usado só pro metadado da resposta (PageImpl abaixo).
    Pageable pageableWithoutSort = pageable.isPaged()
      ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
      : Pageable.unpaged();

    List<TransactionsErpModel> content = total == 0
      ? List.of()
      : transactionErpRepository.findAll(dataSpec, pageableWithoutSort)
      .stream()
      .map(transactionsErpModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<TransactionErpSalesFilter> query) {
    Specification<TransactionErpEntity> spec = transactionErpSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      TransactionErpEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue",
      "adjustment",
      "adjustmentValue"
    );
  }
}
