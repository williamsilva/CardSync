package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.model.nofileday.NoFileDayRequestModel;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.NoFileDayFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.NoFileDayEntity;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.BankRepository;
import com.cardsync.domain.repository.NoFileDayRepository;
import com.cardsync.infrastructure.repository.spec.NoFileDaySpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoFileDayService {

  private final NoFileDaySpecs noFileDaySpecs;
  private final BankRepository bankRepository;
  private final AcquirerRepository acquirerRepository;
  private final NoFileDayRepository noFileDayRepository;

  @Transactional(readOnly = true)
  public NoFileDayEntity getById(UUID noFileDayId) {
    return noFileDayRepository.findById(noFileDayId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "NoFileDay not found for id " + noFileDayId
      ));
  }
  
  @Transactional(readOnly = true)
  public Page<NoFileDayEntity> search(Pageable pageable, ListQueryDto<NoFileDayFilter> query) {
    var spec = noFileDaySpecs.fromQuery(query);
    return noFileDayRepository.findAll(spec, pageable);
  }

  @Transactional
  public NoFileDayEntity create(NoFileDayRequestModel request) {
    NoFileDayEntity entity = new NoFileDayEntity();
    apply(entity, request);
    return noFileDayRepository.save(entity);
  }

  @Transactional
  public NoFileDayEntity update(UUID id, NoFileDayRequestModel request) {
    NoFileDayEntity entity = load(id);
    apply(entity, request);
    return noFileDayRepository.save(entity);
  }

  @Transactional
  public void delete(UUID id) {
    noFileDayRepository.delete(load(id));
  }

  @Transactional
  public void activate(UUID noFileDayId) {
    NoFileDayEntity entity = getById(noFileDayId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.INACTIVE && currentStatus != StatusEnum.BLOCKED) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only INACTIVE or BLOCKED noFileDay can be activated. Current status: " + currentStatus
      );
    }
    entity.activate();
  }

  @Transactional
  public void deactivate(UUID noFileDayId) {
    NoFileDayEntity entity = getById(noFileDayId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE noFileDay can be deactivated. Current status: " + currentStatus
      );
    }
    entity.inactivate();
  }

  @Transactional
  public void block(UUID noFileDayId) {
    NoFileDayEntity entity = getById(noFileDayId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE noFileDay can be blocked. Current status: " + currentStatus
      );
    }
    entity.block();
  }

  @Transactional
  public void activateBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      StatusEnum currentStatus = entity.getStatus();
      if (currentStatus != StatusEnum.INACTIVE && currentStatus != StatusEnum.BLOCKED) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only INACTIVE or BLOCKED noFileDay can be activated. Acquirer id: " + entity.getId()
        );
      }
      entity.setStatus(StatusEnum.ACTIVE);
    });
  }

  @Transactional
  public void deactivateBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      StatusEnum currentStatus = entity.getStatus();
      if (currentStatus != StatusEnum.ACTIVE) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only ACTIVE noFileDay can be deactivated. Acquirer id: " + entity.getId()
        );
      }
      entity.setStatus(StatusEnum.INACTIVE);
    });
  }

  @Transactional
  public void blockBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      StatusEnum currentStatus = entity.getStatus();
      if (currentStatus != StatusEnum.ACTIVE) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only ACTIVE holiday can be blocked. holiday id: " + entity.getId()
        );
      }
      entity.setStatus(StatusEnum.BLOCKED);
    });
  }

  private void updateBulkStatus(List<UUID> ids, Consumer<NoFileDayEntity> updater) {
    if (ids == null || ids.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of acquirer ids must not be empty."
      );
    }

    List<UUID> distinctIds = ids.stream()
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    if (distinctIds.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of acquirer ids must not be empty."
      );
    }

    List<NoFileDayEntity> entities = noFileDayRepository.findAllById(distinctIds);

    Map<UUID, NoFileDayEntity> byId = entities.stream()
      .collect(Collectors.toMap(NoFileDayEntity::getId, e -> e));

    List<UUID> missingIds = distinctIds.stream()
      .filter(id -> !byId.containsKey(id))
      .toList();

    if (!missingIds.isEmpty()) {
      throw BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "Acquirer not found for ids " + missingIds
      );
    }

    entities.forEach(updater);
    noFileDayRepository.saveAll(entities);
  }

  private NoFileDayEntity load(UUID id) {
    return noFileDayRepository.findById(id)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Dia sem arquivo não encontrado: " + id
      ));
  }

  private void apply(NoFileDayEntity entity, NoFileDayRequestModel request) {
    entity.setNoFileDate(request.noFileDate());
    entity.setDescription(request.description().trim());
    entity.setDayType(request.dayType());
    entity.setStatus(request.status() != null ? request.status() : StatusEnum.ACTIVE);

    FileGroupEnum group = request.fileGroup();
    entity.setFileGroup(group);

    // Resolve banco/adquirente conforme o grupo; limpa o que não se aplica.
    if (group == FileGroupEnum.BANK) {
      entity.setAcquirer(null);
      entity.setBank(request.bankId() == null ? null : loadBank(request.bankId()));
    } else if (group == FileGroupEnum.ADQ) {
      entity.setBank(null);
      entity.setAcquirer(request.acquirerId() == null ? null : loadAcquirer(request.acquirerId()));
    } else {
      // ERP não tem banco/adquirente específico.
      entity.setBank(null);
      entity.setAcquirer(null);
    }
  }

  private BankEntity loadBank(UUID bankId) {
    return bankRepository.findById(bankId)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "Banco não encontrado: " + bankId));
  }

  private AcquirerEntity loadAcquirer(UUID acquirerId) {
    return acquirerRepository.findById(acquirerId)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "Adquirente não encontrada: " + acquirerId));
  }
}