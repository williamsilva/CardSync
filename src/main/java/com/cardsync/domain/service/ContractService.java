package com.cardsync.domain.service;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.ContractFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.repository.ContractRepository;
import com.cardsync.infrastructure.repository.spec.ContractSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractService {

  private final ContractSpecs contractSpecs;
  private final ContractRepository contractRepository;
  
  @Transactional(readOnly = true)
  public ContractEntity getById(UUID contractId) {
    return contractRepository.findById(contractId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "Contract not found for id " + contractId
      ));
  }

  @Transactional(readOnly = true)
  public Page<ContractEntity> list(Pageable pageable, ListQueryDto<ContractFilter> query) {
    Specification<ContractEntity> spec = contractSpecs.fromQuery(query);
    return contractRepository.findAll(spec, pageable);
  }
}
