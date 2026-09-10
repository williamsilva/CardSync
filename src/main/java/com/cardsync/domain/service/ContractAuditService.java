package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.ContractAuditModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.domain.filter.ContractAuditModelFilter;
import com.nimbussystems.commons.legacy.filter.query.ListQueryDto;
import com.cardsync.domain.model.*;
import com.cardsync.domain.repository.ContractAuditRepository;
import com.cardsync.infrastructure.repository.spec.ContractAuditSpecs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractAuditService {

  private final ContractAuditSpecs contractAuditSpecs;
  private final ContractAuditRepository contractAuditRepository;
  private final ContractAuditModelAssembler contractAuditModelAssembler;

  @Transactional(readOnly = true)
  public Page<ContractAuditModel> divergentFees(Pageable pageable, ListQueryDto<ContractAuditModelFilter> query) {
    Specification<ContractAuditEntity> filterSpec = contractAuditSpecs.fromQueryForTotals(query);
    Specification<ContractAuditEntity> dataSpec   = contractAuditSpecs.fromQuery(query);

    long total = contractAuditRepository.count(filterSpec);

    // Pageable só de page/size (sem sort): dataSpec já monta o ORDER BY completo via
    // orderByTableSort/tableSort (com os aliases de colunas ligadas por join).
    // SimpleJpaRepository#findAll(Specification, Pageable) reaplica pageable.getSort() por
    // cima, resolvendo o nome bruto direto contra ContractAuditEntity (sem conhecer os
    // aliases) — sort por qualquer coluna que não seja campo direto da entidade quebra com "No
    // property 'X' found for type 'ContractAuditEntity'" (mesmo padrão de CreditOrderService/
    // AnticipationService, achado real 2026-09-10). O pageable original (com sort) continua
    // sendo usado só pro metadado da resposta (PageImpl abaixo).
    Pageable pageableWithoutSort = pageable.isPaged()
      ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
      : Pageable.unpaged();

    List<ContractAuditModel> content = total == 0
      ? List.of()
      : contractAuditRepository.findAll(dataSpec, pageableWithoutSort)
      .stream()
      .map(contractAuditModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }
}
