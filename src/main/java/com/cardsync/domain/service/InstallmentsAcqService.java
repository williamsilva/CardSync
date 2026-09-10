package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.InstallmentsAcqModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.InstallmentAcqModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.filter.InstallmentsAcqFilter;
import com.nimbussystems.commons.legacy.filter.query.ListQueryDto;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.InstallmentsAcqSpecs;
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
public class InstallmentsAcqService {

  private final InstallmentsAcqSpecs installmentsAcqSpecs;
  private final InstallmentAcqRepository installmentAcqRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final InstallmentsAcqModelAssembler installmentAcqModelAssembler;

  @Transactional(readOnly = true)
  public Page<InstallmentAcqModel> search(Pageable pageable, ListQueryDto<InstallmentsAcqFilter> query) {
    Specification<InstallmentAcqEntity> filterSpec = installmentsAcqSpecs.fromQueryForTotals(query);
    Specification<InstallmentAcqEntity> dataSpec   = installmentsAcqSpecs.fromQuery(query);

    long total = installmentAcqRepository.count(filterSpec);

    // Pageable só de page/size (sem sort): dataSpec já monta o ORDER BY completo via
    // orderByTableSort/tableSort (com os aliases de colunas ligadas por join).
    // SimpleJpaRepository#findAll(Specification, Pageable) reaplica pageable.getSort() por
    // cima, resolvendo o nome bruto direto contra InstallmentAcqEntity (sem conhecer os
    // aliases) — sort por qualquer coluna que não seja campo direto da entidade quebra com "No
    // property 'X' found for type 'InstallmentAcqEntity'" (mesmo padrão de CreditOrderService/
    // AnticipationService, achado real 2026-09-10). O pageable original (com sort) continua
    // sendo usado só pro metadado da resposta (PageImpl abaixo).
    Pageable pageableWithoutSort = pageable.isPaged()
      ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
      : Pageable.unpaged();

    List<InstallmentAcqModel> content = total == 0
      ? List.of()
      : installmentAcqRepository.findAll(dataSpec, pageableWithoutSort)
      .stream()
      .map(installmentAcqModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<InstallmentsAcqFilter> query) {
    Specification<InstallmentAcqEntity> spec = installmentsAcqSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      InstallmentAcqEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue"
    );
  }
}
