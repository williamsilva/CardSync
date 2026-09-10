package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.InstallmentsErpModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.InstallmentErpModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.filter.InstallmentsErpFilter;
import com.nimbussystems.commons.legacy.filter.query.ListQueryDto;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.repository.InstallmentErpRepository;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.InstallmentsErpSpecs;
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
public class InstallmentsErpService {

  private final InstallmentsErpSpecs installmentsErpSpecs;
  private final InstallmentErpRepository installmentErpRepository;
  private final TransactionTotalsQueryService totalsQueryService;
  private final InstallmentsErpModelAssembler installmentErpModelAssembler;

  @Transactional(readOnly = true)
  public Page<InstallmentErpModel> search(Pageable pageable, ListQueryDto<InstallmentsErpFilter> query) {
    Specification<InstallmentErpEntity> filterSpec = installmentsErpSpecs.fromQueryForTotals(query);
    Specification<InstallmentErpEntity> dataSpec   = installmentsErpSpecs.fromQuery(query);

    long total = installmentErpRepository.count(filterSpec);

    // Pageable só de page/size (sem sort): dataSpec já monta o ORDER BY completo via
    // orderByTableSort/tableSort (com os aliases de colunas ligadas por join).
    // SimpleJpaRepository#findAll(Specification, Pageable) reaplica pageable.getSort() por
    // cima, resolvendo o nome bruto direto contra InstallmentErpEntity (sem conhecer os
    // aliases) — sort por qualquer coluna que não seja campo direto da entidade quebra com "No
    // property 'X' found for type 'InstallmentErpEntity'" (mesmo padrão de CreditOrderService/
    // AnticipationService, achado real 2026-09-10). O pageable original (com sort) continua
    // sendo usado só pro metadado da resposta (PageImpl abaixo).
    Pageable pageableWithoutSort = pageable.isPaged()
      ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
      : Pageable.unpaged();

    List<InstallmentErpModel> content = total == 0
      ? List.of()
      : installmentErpRepository.findAll(dataSpec, pageableWithoutSort)
      .stream()
      .map(installmentErpModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<InstallmentsErpFilter> query) {
    Specification<InstallmentErpEntity> spec = installmentsErpSpecs.fromQueryForTotals(query);

    return totalsQueryService.totals(
      InstallmentErpEntity.class,
      spec,
      "grossValue",
      "discountValue",
      "liquidValue"
    );
  }
}
