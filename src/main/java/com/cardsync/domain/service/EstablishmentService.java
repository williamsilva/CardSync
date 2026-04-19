package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.EstablishmentInput;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.EstablishmentFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.RelationAcquirerEstablishmentEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.model.enums.TypeEstablishmentEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.infrastructure.repository.spec.EstablishmentSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EstablishmentService {

  private final CompanyRepository companyRepository;
  private final EstablishmentSpecs establishmentSpecs;
  private final AcquirerRepository acquirerRepository;
  private final EstablishmentRepository establishmentRepository;

  @Transactional(readOnly = true)
  public List<EstablishmentEntity> listOptionsFilter() {
    return establishmentRepository
      .findAll(Sort.by(Sort.Direction.ASC, "pvNumber"));
  }

  @Transactional(readOnly = true)
  public EstablishmentEntity getById(UUID establishmentId) {
    return establishmentRepository.findById(establishmentId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.ESTABLISHMENT_NOT_FOUND,
        "Establishment not found for id " + establishmentId
      ));
  }

  @Transactional(readOnly = true)
  public Page<EstablishmentEntity> list(Pageable pageable, ListQueryDto<EstablishmentFilter> query) {
    Specification<EstablishmentEntity> spec = establishmentSpecs.fromQuery(query);
    return establishmentRepository.findAll(spec, pageable);
  }

  @Transactional
  public EstablishmentEntity create(EstablishmentInput input) {
    validateRequiredFields(input);
    validateUniqueCombination(input.pvNumber(), input.companyId(), input.acquirerId(), null);

    CompanyEntity company = getCompanyById(input.companyId());
    AcquirerEntity acquirer = getAcquirerById(input.acquirerId());

    EstablishmentEntity entity = new EstablishmentEntity();
    entity.setPvNumber(input.pvNumber());
    entity.setCompany(company);
    entity.setAcquirer(acquirer);
    entity.setType(input.type());
    entity.setStatus(input.status());

    try {
      EstablishmentEntity saved = establishmentRepository.saveAndFlush(entity);
      ensureAcquirerEstablishmentRelation(acquirer, saved);

      return establishmentRepository.findById(saved.getId())
        .orElseThrow(() -> BusinessException.notFound(
          ErrorCode.COMPANY_NOT_FOUND,
          "Establishment not found for id " + saved.getId()
        ));
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      throw BusinessException.badRequest(
        ErrorCode.ESTABLISHMENT_ALREADY_EXISTS,
        "There is already an establishment with the same pvNumber, company, and acquirer."
      );
    }
  }

  @Transactional
  public EstablishmentEntity update(UUID establishmentId, EstablishmentInput input) {
    EstablishmentEntity entity = getById(establishmentId);

    AcquirerEntity oldAcquirer = entity.getAcquirer();

    validateRequiredFields(input);
    validateUniqueCombination(input.pvNumber(), input.companyId(), input.acquirerId(), establishmentId);

    CompanyEntity company = getCompanyById(input.companyId());
    AcquirerEntity acquirer = getAcquirerById(input.acquirerId());

    entity.setPvNumber(input.pvNumber());
    entity.setCompany(company);
    entity.setAcquirer(acquirer);
    entity.setType(input.type());
    entity.setStatus(input.status());

    try {
      EstablishmentEntity saved = establishmentRepository.saveAndFlush(entity);

      if (oldAcquirer != null && !oldAcquirer.getId().equals(acquirer.getId())) {
        oldAcquirer.getAcquirerEstablishments().removeIf(rel ->
          rel.getEstablishment() != null && establishmentId.equals(rel.getEstablishment().getId())
        );
        acquirerRepository.save(oldAcquirer);
      }

      ensureAcquirerEstablishmentRelation(acquirer, saved);

      return establishmentRepository.findById(saved.getId())
        .orElseThrow(() -> BusinessException.notFound(
          ErrorCode.COMPANY_NOT_FOUND,
          "Establishment not found for id " + saved.getId()
        ));
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      throw BusinessException.badRequest(
        ErrorCode.ESTABLISHMENT_ALREADY_EXISTS,
        "There is already an establishment with the same pvNumber, company, and acquirer."
      );
    }
  }

  @Transactional
  public void activate(UUID establishmentId) {
    EstablishmentEntity entity = getById(establishmentId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.INACTIVE && currentStatus != StatusEnum.BLOCKED) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only INACTIVE or BLOCKED establishments can be activated. Current status: " + currentStatus
      );
    }
    entity.activate();
  }

  @Transactional
  public void deactivate(UUID establishmentId) {
    EstablishmentEntity entity = getById(establishmentId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE establishments can be deactivated. Current status: " + currentStatus
      );
    }
    entity.inactivate();
  }

  @Transactional
  public void block(UUID establishmentId) {
    EstablishmentEntity entity = getById(establishmentId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE establishments can be blocked. Current status: " + currentStatus
      );
    }
    entity.block();
  }

  @Transactional
  public void delete(UUID establishmentId) {
    EstablishmentEntity entity = getById(establishmentId);

    try {
      establishmentRepository.delete(entity);
      establishmentRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw BusinessException.conflict(
        ErrorCode.ESTABLISHMENT_DELETE_IN_USE,
        "Cannot delete establishment because it has linked records. establishmentId=" + establishmentId
      );
    }
  }

  @Transactional
  public void activateBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      StatusEnum currentStatus = entity.getStatus();
      if (currentStatus != StatusEnum.INACTIVE && currentStatus != StatusEnum.BLOCKED) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only INACTIVE or BLOCKED establishments can be activated. Establishment id: " + entity.getId()
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
          "Only ACTIVE establishments can be deactivated. Establishment id: " + entity.getId()
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
          "Only ACTIVE establishments can be blocked. Establishment id: " + entity.getId()
        );
      }
      entity.setStatus(StatusEnum.BLOCKED);
    });
  }

  private void updateBulkStatus(List<UUID> ids, Consumer<EstablishmentEntity> updater) {
    if (ids == null || ids.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of establishment ids must not be empty."
      );
    }

    List<UUID> distinctIds = ids.stream()
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    if (distinctIds.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of establishment ids must not be empty."
      );
    }

    List<EstablishmentEntity> entities = establishmentRepository.findAllById(distinctIds);

    Map<UUID, EstablishmentEntity> byId = entities.stream()
      .collect(Collectors.toMap(EstablishmentEntity::getId, e -> e));

    List<UUID> missingIds = distinctIds.stream()
      .filter(id -> !byId.containsKey(id))
      .toList();

    if (!missingIds.isEmpty()) {
      throw BusinessException.notFound(
        ErrorCode.ESTABLISHMENT_NOT_FOUND,
        "Establishment not found for ids " + missingIds
      );
    }

    entities.forEach(updater);
    establishmentRepository.saveAll(entities);
  }

  private void validateRequiredFields(EstablishmentInput input) {
    if (input.pvNumber() == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'pvNumber' is required."
      );
    }

    if (input.companyId() == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'companyId' is required."
      );
    }

    if (input.acquirerId() == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'acquirerId' is required."
      );
    }

    if (input.type() == null || input.type() == TypeEstablishmentEnum.NULL) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'type' is required."
      );
    }

    if (input.status() == null || input.status() == StatusEnum.NULL) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'status' is required."
      );
    }
  }

  private void validateUniqueCombination(Integer pvNumber, UUID companyId, UUID acquirerId, UUID currentId) {
    boolean exists = currentId == null
      ? establishmentRepository.existsByPvNumberAndCompany_IdAndAcquirer_Id(
      pvNumber, companyId, acquirerId
    )
      : establishmentRepository.existsByPvNumberAndCompany_IdAndAcquirer_IdAndIdNot(
      pvNumber, companyId, acquirerId, currentId
    );

    if (exists) {
      throw BusinessException.badRequest(
        ErrorCode.ESTABLISHMENT_ALREADY_EXISTS,
        "There is already an establishment with the same pvNumber, company, and acquirer."
      );
    }
  }

  private CompanyEntity getCompanyById(UUID companyId) {
    return companyRepository.findById(companyId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "Company not found for id " + companyId
      ));
  }

  private AcquirerEntity getAcquirerById(UUID acquirerId) {
    return acquirerRepository.findById(acquirerId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.ACQUIRER_NOT_FOUND,
        "Acquirer not found for id " + acquirerId
      ));
  }

  private void ensureAcquirerEstablishmentRelation(AcquirerEntity acquirer, EstablishmentEntity establishment) {
    boolean alreadyLinked = acquirer.getAcquirerEstablishments().stream()
      .anyMatch(rel ->
        rel.getEstablishment() != null
          && rel.getEstablishment().getId() != null
          && rel.getEstablishment().getId().equals(establishment.getId())
      );

    if (!alreadyLinked) {
      RelationAcquirerEstablishmentEntity relation = new RelationAcquirerEstablishmentEntity();
      relation.setAcquirer(acquirer);
      relation.setEstablishment(establishment);
      acquirer.getAcquirerEstablishments().add(relation);
      acquirerRepository.save(acquirer);
    }
  }
}