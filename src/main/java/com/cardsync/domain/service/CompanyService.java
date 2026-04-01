package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.CompanyInput;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.CompanyFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeCompanyEnum;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.infrastructure.repository.spec.CompanySpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyService {

  private final CompanySpecs companySpecs;
  private final CompanyRepository companyRepository;

  @Transactional(readOnly = true)
  public CompanyEntity getById(UUID companyId) {
    return companyRepository.findById(companyId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "Company not found for id " + companyId
      ));
  }

  @Transactional(readOnly = true)
  public Page<CompanyEntity> list(Pageable pageable, ListQueryDto<CompanyFilter> query) {
    Specification<CompanyEntity> spec = companySpecs.fromQuery(query);
    return companyRepository.findAll(spec, pageable);
  }

  @Transactional
  public CompanyEntity create(CompanyInput input) {
    validateRequiredFields(input);

    String normalizedCnpj = normalizeDigits(input.cnpj());
    validateDuplicatedCnpjForCreate(normalizedCnpj);

    CompanyEntity entity = new CompanyEntity();
    entity.setCnpj(normalizedCnpj);
    entity.setFantasyName(input.fantasyName().trim());
    entity.setSocialReason(input.socialReason().trim());
    entity.setType(input.type());
    entity.setStatus(StatusEnum.ACTIVE);

    return companyRepository.save(entity);
  }

  @Transactional
  public CompanyEntity update(UUID companyId, CompanyInput input) {
    CompanyEntity entity = getById(companyId);

    validateRequiredFields(input);

    String normalizedCnpj = normalizeDigits(input.cnpj());
    validateDuplicatedCnpjForUpdate(companyId, normalizedCnpj);

    entity.setType(input.type());
    entity.setCnpj(normalizedCnpj);
    entity.setFantasyName(input.fantasyName().trim());
    entity.setSocialReason(input.socialReason().trim());

    if (input.status() != null && input.status() != StatusEnum.NULL) {
      entity.setStatus(input.status());
    }

    return companyRepository.save(entity);
  }

  @Transactional
  public void activate(UUID companyId) {
    CompanyEntity entity = getById(companyId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.INACTIVE && currentStatus != StatusEnum.BLOCKED) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only INACTIVE or BLOCKED companies can be activated. Current status: " + currentStatus
      );
    }
    entity.activate();
  }

  @Transactional
  public void deactivate(UUID companyId) {
    CompanyEntity entity = getById(companyId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE companies can be deactivated. Current status: " + currentStatus
      );
    }
    entity.inactivate();
  }

  @Transactional
  public void block(UUID companyId) {
    CompanyEntity entity = getById(companyId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE companies can be blocked. Current status: " + currentStatus
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
          "Only INACTIVE or BLOCKED companies can be activated. Company id: " + entity.getId()
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
          "Only ACTIVE companies can be deactivated. Company id: " + entity.getId()
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
          "Only ACTIVE companies can be blocked. Company id: " + entity.getId()
        );
      }
      entity.setStatus(StatusEnum.BLOCKED);
    });
  }

  private void updateBulkStatus(List<UUID> ids, Consumer<CompanyEntity> updater) {
    if (ids == null || ids.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of company ids must not be empty."
      );
    }

    List<UUID> distinctIds = ids.stream()
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    if (distinctIds.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of company ids must not be empty."
      );
    }

    List<CompanyEntity> entities = companyRepository.findAllById(distinctIds);

    Map<UUID, CompanyEntity> byId = entities.stream()
      .collect(Collectors.toMap(CompanyEntity::getId, e -> e));

    List<UUID> missingIds = distinctIds.stream()
      .filter(id -> !byId.containsKey(id))
      .toList();

    if (!missingIds.isEmpty()) {
      throw BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "Company not found for ids " + missingIds
      );
    }

    entities.forEach(updater);
    companyRepository.saveAll(entities);
  }

  private void validateRequiredFields(CompanyInput input) {
    if (input.cnpj() == null || normalizeDigits(input.cnpj()).isBlank()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'cnpj' is required."
      );
    }

    if (input.fantasyName() == null || input.fantasyName().isBlank()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'fantasyName' is required."
      );
    }

    if (input.socialReason() == null || input.socialReason().isBlank()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'socialReason' is required."
      );
    }

    if (input.type() == null || input.type() == TypeCompanyEnum.NULL) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'type' is required."
      );
    }
  }

  private void validateDuplicatedCnpjForCreate(String normalizedCnpj) {
    boolean exists = companyRepository.existsByCnpj(normalizedCnpj);

    if (exists) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "There is already a company registered with this cnpj."
      );
    }
  }

  private void validateDuplicatedCnpjForUpdate(UUID companyId, String normalizedCnpj) {
    Optional<CompanyEntity> existing = companyRepository.findByCnpj(normalizedCnpj);

    if (existing.isPresent() && !existing.get().getId().equals(companyId)) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "There is already a company registered with this cnpj."
      );
    }
  }

  private String normalizeDigits(String value) {
    return value == null ? "" : value.replaceAll("\\D+", "");
  }
}