package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.ContractAuditModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.conciliation.*;
import com.cardsync.domain.filter.ContractAuditModelFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.*;
import com.cardsync.domain.repository.ContractAuditRepository;
import com.cardsync.infrastructure.repository.spec.ContractAuditSpecs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractAuditService {

  private final ContractAuditSpecs contractAuditSpecs;
  private final ContractAuditRepository contractAuditRepository;
  private final ContractAuditModelAssembler contractAuditModelAssembler;

  @Transactional(readOnly = true)
  public Page<ContractAuditModel> divergentFees(Pageable pageable, ListQueryDto<ContractAuditModelFilter> query) {
      Specification<ContractAuditEntity> spec = contractAuditSpecs.fromQuery(query);
    Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

    return contractAuditRepository
      .findAll(spec, unsortedPageable)
      .map(contractAuditModelAssembler::toModel);
  }
}
