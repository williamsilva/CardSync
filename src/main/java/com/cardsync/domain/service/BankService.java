package com.cardsync.domain.service;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.BankFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.BankRepository;
import com.cardsync.infrastructure.repository.spec.BankSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;

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
      .findAllOptionsFilterOrderByActiveThenName(StatusEnum.toCode(StatusEnum.ACTIVE));
  }

  @Transactional(readOnly = true)
  public Page<BankEntity> search(Pageable pageable, ListQueryDto<BankFilter> query) {
    var spec = bankSpecs.fromQuery(query);
    return bankRepository.findAll(spec, pageable);
  }

  @Transactional
  public void activate(UUID bankId) {
    BankEntity entity = getById(bankId);
    StatusEnum current = entity.getStatus();
    if (current != StatusEnum.INACTIVE && current != StatusEnum.BLOCKED) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only INACTIVE or BLOCKED banks can be activated. Current status: " + current
      );
    }
    entity.activate();
  }

  @Transactional
  public void deactivate(UUID bankId) {
    BankEntity entity = getById(bankId);
    if (entity.getStatus() != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE banks can be deactivated. Current status: " + entity.getStatus()
      );
    }
    entity.inactivate();
  }

  @Transactional
  public void block(UUID bankId) {
    BankEntity entity = getById(bankId);
    if (entity.getStatus() != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE banks can be blocked. Current status: " + entity.getStatus()
      );
    }
    entity.block();
  }

  @Transactional
  public void activateBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      StatusEnum current = entity.getStatus();
      if (current != StatusEnum.INACTIVE && current != StatusEnum.BLOCKED) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only INACTIVE or BLOCKED banks can be activated. Bank id: " + entity.getId()
        );
      }
      entity.setStatus(StatusEnum.ACTIVE);
    });
  }

  @Transactional
  public void deactivateBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      if (entity.getStatus() != StatusEnum.ACTIVE) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only ACTIVE banks can be deactivated. Bank id: " + entity.getId()
        );
      }
      entity.setStatus(StatusEnum.INACTIVE);
    });
  }

  @Transactional
  public void blockBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      if (entity.getStatus() != StatusEnum.ACTIVE) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only ACTIVE banks can be blocked. Bank id: " + entity.getId()
        );
      }
      entity.setStatus(StatusEnum.BLOCKED);
    });
  }

  private void updateBulkStatus(List<UUID> ids, Consumer<BankEntity> updater) {
    if (ids == null || ids.isEmpty()) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "The list of bank ids must not be empty.");
    }

    List<UUID> distinctIds = ids.stream()
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    if (distinctIds.isEmpty()) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "The list of bank ids must not be empty.");
    }

    List<BankEntity> entities = bankRepository.findAllById(distinctIds);

    Map<UUID, BankEntity> byId = entities.stream()
      .collect(java.util.stream.Collectors.toMap(BankEntity::getId, e -> e));

    List<UUID> missing = distinctIds.stream().filter(id -> !byId.containsKey(id)).toList();
    if (!missing.isEmpty()) {
      throw BusinessException.notFound(ErrorCode.NOT_FOUND, "Bank not found for ids " + missing);
    }

    entities.forEach(updater);
    bankRepository.saveAll(entities);
  }
}