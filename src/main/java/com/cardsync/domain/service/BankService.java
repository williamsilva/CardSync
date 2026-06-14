package com.cardsync.domain.service;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.BankFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.*;
import com.cardsync.domain.repository.BankRepository;
import com.cardsync.infrastructure.repository.spec.BankSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BankService {

  private final BankSpecs bankSpecs;
  private final BankRepository bankRepository;

  @Transactional(readOnly = true)
  public BankEntity getById(UUID bankId) {
    return bankRepository.findById(bankId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Bank not found for id " + bankId
      ));
  }

  @Transactional(readOnly = true)
  public List<BankEntity> listOptionsFilter() {
    return bankRepository
      .findAll(Sort.by(Sort.Direction.ASC, "name", "status"));
  }

  @Transactional(readOnly = true)
  public Page<BankEntity> search(Pageable pageable, ListQueryDto<BankFilter> query) {
    var spec = bankSpecs.fromQuery(query);
    return bankRepository.findAll(spec, pageable);
  }

}