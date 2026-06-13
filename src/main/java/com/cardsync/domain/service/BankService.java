package com.cardsync.domain.service;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.*;
import com.cardsync.domain.repository.BankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BankService {

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
}