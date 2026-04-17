package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.AcquirerInput;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.AcquirerFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.infrastructure.repository.spec.AcquirerSpecs;
import lombok.RequiredArgsConstructor;
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
public class AcquirerService {

  private final AcquirerSpecs acquirerSpecs;
  private final CompanyRepository companyRepository;
  private final AcquirerRepository acquirerRepository;
  private final EstablishmentRepository establishmentRepository;

  @Transactional(readOnly = true)
  public List<AcquirerEntity> listOptionsFilter() {
    return acquirerRepository
      .findAll(Sort.by(Sort.Direction.ASC, "fantasyName", "socialReason"));
  }

  @Transactional(readOnly = true)
  public AcquirerEntity getById(UUID acquirerId) {
    AcquirerEntity entity = acquirerRepository.findById(acquirerId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "Acquirer not found for id " + acquirerId
      ));

    initializeRelations(entity);
    return entity;
  }

  @Transactional(readOnly = true)
  public Page<AcquirerEntity> list(Pageable pageable, ListQueryDto<AcquirerFilter> query) {
    Specification<AcquirerEntity> spec = acquirerSpecs.fromQuery(query);

    Page<AcquirerEntity> page = acquirerRepository.findAll(spec, pageable);
    page.getContent().forEach(this::initializeRelations);

    return page;
  }

  @Transactional
  public AcquirerEntity create(AcquirerInput input) {
    validateRequiredFields(input);

    String normalizedCnpj = normalizeDigits(input.cnpj());
    validateDuplicatedCnpjForCreate(normalizedCnpj);

    AcquirerEntity entity = new AcquirerEntity();
    entity.setCnpj(normalizedCnpj);
    entity.setFantasyName(input.fantasyName().trim());
    entity.setSocialReason(input.socialReason().trim());

    entity.setStatus(StatusEnum.ACTIVE);

    return acquirerRepository.save(entity);
  }

  @Transactional
  public AcquirerEntity update(UUID acquirerId, AcquirerInput input) {
    AcquirerEntity entity = getById(acquirerId);

    validateRequiredFields(input);

    String normalizedCnpj = normalizeDigits(input.cnpj());
    validateDuplicatedCnpjForUpdate(acquirerId, normalizedCnpj);

    entity.setCnpj(normalizedCnpj);
    entity.setFantasyName(input.fantasyName().trim());
    entity.setSocialReason(input.socialReason().trim());

    if (input.status() != null && input.status() != StatusEnum.NULL) {
      entity.setStatus(input.status());
    }

    return acquirerRepository.save(entity);
  }

  @Transactional
  public void activate(UUID acquirerId) {
    AcquirerEntity entity = getById(acquirerId);
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
  public void deactivate(UUID acquirerId) {
    AcquirerEntity entity = getById(acquirerId);
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
  public void block(UUID acquirerId) {
    AcquirerEntity entity = getById(acquirerId);
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
          "Only INACTIVE or BLOCKED companies can be activated. Acquirer id: " + entity.getId()
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
          "Only ACTIVE companies can be deactivated. Acquirer id: " + entity.getId()
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
          "Only ACTIVE companies can be blocked. Acquirer id: " + entity.getId()
        );
      }
      entity.setStatus(StatusEnum.BLOCKED);
    });
  }

  @Transactional
  public AcquirerEntity addCompaniesRelations(UUID acquirerId, List<UUID> companyIds) {
    AcquirerEntity acquirer = getById(acquirerId);

    List<UUID> ids = distinctIds(companyIds);
    List<CompanyEntity> companies = ids.isEmpty() ? List.of() : companyRepository.findAllById(ids);

    validateAllIdsFound(ids, companies.stream().map(CompanyEntity::getId).toList(), "Company");

    Set<UUID> existingIds = acquirer.getAcquirerCompanies().stream()
      .filter(item -> item.getCompany() != null)
      .map(item -> item.getCompany().getId())
      .collect(Collectors.toSet());

    for (CompanyEntity company : companies) {
      if (existingIds.contains(company.getId())) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Company already linked to this acquirer: " + company.getId()
        );
      }

      RelationAcquirerCompanyEntity relation = new RelationAcquirerCompanyEntity();
      relation.setAcquirer(acquirer);
      relation.setCompany(company);
      acquirer.getAcquirerCompanies().add(relation);
    }

    return acquirerRepository.save(acquirer);
  }

  @Transactional
  public AcquirerEntity removeCompanyRelations(UUID acquirerId, UUID companyId) {
    AcquirerEntity acquirer = getById(acquirerId);

    boolean removed = acquirer.getAcquirerCompanies().removeIf(item ->
      item.getCompany() != null && companyId.equals(item.getCompany().getId())
    );

    if (!removed) {
      throw BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Company relation not found for acquirerId=%s and companyId=%s".formatted(acquirerId, companyId)
      );
    }

    return acquirerRepository.save(acquirer);
  }

  @Transactional
  public AcquirerEntity addEstablishmentRelations(UUID acquirerId, List<UUID> establishmentIds) {
    AcquirerEntity acquirer = getById(acquirerId);

    List<UUID> ids = distinctIds(establishmentIds);
    List<EstablishmentEntity> establishments = ids.isEmpty() ? List.of() : establishmentRepository.findAllById(ids);

    validateAllIdsFound(ids, establishments.stream().map(EstablishmentEntity::getId).toList(), "Establishment");

    Set<UUID> existingIds = acquirer.getAcquirerEstablishments().stream()
      .filter(item -> item.getEstablishment() != null)
      .map(item -> item.getEstablishment().getId())
      .collect(Collectors.toSet());

    for (EstablishmentEntity establishment : establishments) {
      if (existingIds.contains(establishment.getId())) {
        throw BusinessException.conflict(
          ErrorCode.VALIDATION_ERROR,
          "Establishment already linked to this acquirer: " + establishment.getId()
        );
      }

      RelationAcquirerEstablishmentEntity relation = new RelationAcquirerEstablishmentEntity();
      relation.setAcquirer(acquirer);
      relation.setEstablishment(establishment);
      acquirer.getAcquirerEstablishments().add(relation);
    }

    return acquirerRepository.save(acquirer);
  }

  @Transactional
  public AcquirerEntity removeEstablishmentRelations(UUID acquirerId, UUID establishmentId) {
    AcquirerEntity acquirer = getById(acquirerId);

    boolean removed = acquirer.getAcquirerEstablishments().removeIf(item ->
      item.getAcquirer() != null && establishmentId.equals(item.getAcquirer().getId())
    );

    if (!removed) {
      throw BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Acquirer relation not found for acquirerId=%s and establishmentId=%s".formatted(acquirerId, establishmentId)
      );
    }

    return acquirerRepository.save(acquirer);
  }

  private void initializeRelations(AcquirerEntity acquirer) {
    acquirer.getAcquirerCompanies().forEach(rel -> {
      if (rel.getCompany() != null) {
        rel.getCompany().getId();
        rel.getCompany().getFantasyName();
        rel.getCompany().getSocialReason();
        rel.getCompany().getCnpj();
        rel.getCompany().getStatus();
        rel.getCompany().getType();
      }
    });

    acquirer.getAcquirerEstablishments().forEach(rel -> {
      if (rel.getEstablishment() != null) {
        rel.getEstablishment().getId();
        rel.getEstablishment().getPvNumber();
        rel.getEstablishment().getStatus();
        rel.getEstablishment().getType();
      }
    });
  }

  private List<UUID> distinctIds(List<UUID> ids) {
    if (ids == null) return List.of();
    return ids.stream().filter(Objects::nonNull).distinct().toList();
  }

  private void validateAllIdsFound(List<UUID> requested, List<UUID> found, String label) {
    Set<UUID> foundSet = new HashSet<>(found);
    List<UUID> missing = requested.stream().filter(id -> !foundSet.contains(id)).toList();
    if (!missing.isEmpty()) {
      throw BusinessException.notFound(ErrorCode.NOT_FOUND, label + " not found for ids " + missing);
    }
  }

  private void updateBulkStatus(List<UUID> ids, Consumer<AcquirerEntity> updater) {
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

    List<AcquirerEntity> entities = acquirerRepository.findAllById(distinctIds);

    Map<UUID, AcquirerEntity> byId = entities.stream()
      .collect(Collectors.toMap(AcquirerEntity::getId, e -> e));

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
    acquirerRepository.saveAll(entities);
  }

  private void validateRequiredFields(AcquirerInput input) {
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

  }

  private void validateDuplicatedCnpjForCreate(String normalizedCnpj) {
    boolean exists = acquirerRepository.existsByCnpj(normalizedCnpj);

    if (exists) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "There is already a acquirer registered with this cnpj."
      );
    }
  }

  private void validateDuplicatedCnpjForUpdate(UUID acquirerId, String normalizedCnpj) {
    Optional<AcquirerEntity> existing = acquirerRepository.findByCnpj(normalizedCnpj);

    if (existing.isPresent() && !existing.get().getId().equals(acquirerId)) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "There is already a acquirer registered with this cnpj."
      );
    }
  }

  private String normalizeDigits(String value) {
    return value == null ? "" : value.replaceAll("\\D+", "");
  }
}