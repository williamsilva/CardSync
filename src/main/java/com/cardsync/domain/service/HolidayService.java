package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.model.holiday.HolidayRequestModel;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.HolidayFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.HolidayEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.HolidayRepository;
import com.cardsync.infrastructure.repository.spec.HolidaySpecs;
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
public class HolidayService {

  private final HolidaySpecs holidaySpecs;
  private final HolidayRepository holidayRepository;

  @Transactional(readOnly = true)
  public HolidayEntity getById(UUID holidayId) {
   return holidayRepository.findById(holidayId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "Holiday not found for id " + holidayId
      ));
  }
  
  @Transactional(readOnly = true)
  public Page<HolidayEntity> search(Pageable pageable, ListQueryDto<HolidayFilter> query) {
    var spec = holidaySpecs.fromQuery(query);
    return holidayRepository.findAll(spec, pageable);
  }

  @Transactional
  public HolidayEntity create(HolidayRequestModel request) {
    holidayRepository.findByHolidayDate(request.holidayDate()).ifPresent(existing -> {
      throw BusinessException.conflict(ErrorCode.BUSINESS_ERROR,
        "Já existe um feriado cadastrado para " + request.holidayDate());
    });

    HolidayEntity entity = new HolidayEntity();
    apply(entity, request);
    return (holidayRepository.save(entity));
  }

  @Transactional
  public HolidayEntity update(UUID id, HolidayRequestModel request) {
    HolidayEntity entity = load(id);

    holidayRepository.findByHolidayDate(request.holidayDate())
      .filter(existing -> !existing.getId().equals(id))
      .ifPresent(existing -> {
        throw BusinessException.conflict(ErrorCode.BUSINESS_ERROR,
          "Já existe um feriado cadastrado para " + request.holidayDate());
      });

    apply(entity, request);
    return (holidayRepository.save(entity));
  }

  @Transactional
  public void delete(UUID id) {
    holidayRepository.delete(load(id));
  }

  @Transactional
  public void activate(UUID holidayId) {
    HolidayEntity entity = getById(holidayId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.INACTIVE && currentStatus != StatusEnum.BLOCKED) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only INACTIVE or BLOCKED holiday can be activated. Current status: " + currentStatus
      );
    }
    entity.activate();
  }

  @Transactional
  public void deactivate(UUID holidayId) {
    HolidayEntity entity = getById(holidayId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE holiday can be deactivated. Current status: " + currentStatus
      );
    }
    entity.inactivate();
  }

  @Transactional
  public void block(UUID holidayId) {
    HolidayEntity entity = getById(holidayId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE holiday can be blocked. Current status: " + currentStatus
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
          "Only INACTIVE or BLOCKED holiday can be activated. Acquirer id: " + entity.getId()
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
          "Only ACTIVE holiday can be deactivated. Acquirer id: " + entity.getId()
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

  private void updateBulkStatus(List<UUID> ids, Consumer<HolidayEntity> updater) {
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

    List<HolidayEntity> entities = holidayRepository.findAllById(distinctIds);

    Map<UUID, HolidayEntity> byId = entities.stream()
      .collect(Collectors.toMap(HolidayEntity::getId, e -> e));

    List<UUID> missingIds = distinctIds.stream()
      .filter(id -> !byId.containsKey(id))
      .toList();

    if (!missingIds.isEmpty()) {
      throw BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "Holiday not found for ids " + missingIds
      );
    }

    entities.forEach(updater);
    holidayRepository.saveAll(entities);
  }
  
  private HolidayEntity load(UUID id) {
    return holidayRepository.findById(id)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Feriado não encontrado: " + id
      ));
  }

  private void apply(HolidayEntity entity, HolidayRequestModel request) {
    entity.setHolidayDate(request.holidayDate());
    entity.setName(request.name().trim());
    entity.setStatus(request.status() != null ? request.status() : StatusEnum.ACTIVE);
  }


}