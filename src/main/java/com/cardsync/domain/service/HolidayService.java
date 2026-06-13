package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.model.holiday.HolidayModel;
import com.cardsync.bff.controller.v1.representation.model.holiday.HolidayRequestModel;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.HolidayEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HolidayService {

  private final HolidayRepository holidayRepository;

  @Transactional(readOnly = true)
  public List<HolidayModel> list() {
    return holidayRepository.findAllByOrderByHolidayDateDesc().stream()
      .map(this::toModel)
      .toList();
  }

  @Transactional
  public HolidayModel create(HolidayRequestModel request) {
    holidayRepository.findByHolidayDate(request.holidayDate()).ifPresent(existing -> {
      throw BusinessException.conflict(ErrorCode.BUSINESS_ERROR,
        "Já existe um feriado cadastrado para " + request.holidayDate());
    });

    HolidayEntity entity = new HolidayEntity();
    apply(entity, request);
    return toModel(holidayRepository.save(entity));
  }

  @Transactional
  public HolidayModel update(UUID id, HolidayRequestModel request) {
    HolidayEntity entity = load(id);

    holidayRepository.findByHolidayDate(request.holidayDate())
      .filter(existing -> !existing.getId().equals(id))
      .ifPresent(existing -> {
        throw BusinessException.conflict(ErrorCode.BUSINESS_ERROR,
          "Já existe um feriado cadastrado para " + request.holidayDate());
      });

    apply(entity, request);
    return toModel(holidayRepository.save(entity));
  }

  @Transactional
  public void delete(UUID id) {
    holidayRepository.delete(load(id));
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

  private HolidayModel toModel(HolidayEntity entity) {
    return new HolidayModel(
      entity.getId(),
      entity.getHolidayDate(),
      entity.getName(),
      entity.getStatus(),
      entity.getStatusDate(),
      entity.getCreatedAt(),
      entity.getUpdatedAt()
    );
  }
}
