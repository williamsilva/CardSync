package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.FlagAcquirerRelationItemInput;
import com.cardsync.bff.controller.v1.representation.input.FlagInput;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.FlagFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.*;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.FlagRepository;
import com.cardsync.infrastructure.repository.spec.FlagSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlagService {

  private final FlagSpecs flagSpecs;
  private final FlagRepository flagRepository;
  private final CompanyRepository companyRepository;
  private final AcquirerRepository acquirerRepository;

  @Transactional(readOnly = true)
  public FlagEntity getById(UUID flagId) {
    return flagRepository.findDetailedById(flagId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Flag not found for id " + flagId
      ));
  }

  @Transactional(readOnly = true)
  public List<FlagEntity> listOptionsFilter() {
    return flagRepository
      .findAll(Sort.by(Sort.Direction.ASC, "name", "status"));
  }

  @Transactional(readOnly = true)
  public Page<FlagEntity> list(Pageable pageable, ListQueryDto<FlagFilter> query) {
    Specification<FlagEntity> spec = flagSpecs.fromQuery(query);
    Page<FlagEntity> page = flagRepository.findAll(spec, pageable);

    page.getContent().forEach(flag -> {
      flag.getFlagAcquirers().forEach(rel -> {
        if (rel.getAcquirer() != null) {
          rel.getAcquirer().getId();
          rel.getAcquirer().getFantasyName();
          rel.getAcquirer().getCnpj();
          rel.getAcquirer().getStatus();
          rel.getAcquirer().getSocialReason();
        }
      });

      flag.getFlagCompanies().forEach(rel -> {
        if (rel.getCompany() != null) {
          rel.getCompany().getId();
          rel.getCompany().getFantasyName();
          rel.getCompany().getCnpj();
          rel.getCompany().getStatus();
          rel.getCompany().getSocialReason();
          rel.getCompany().getType();
        }
      });
    });

    return page;
  }

  @Transactional
  public FlagEntity create(FlagInput input) {
    validateRequiredFields(input);

    String normalizedName = normalizeName(input.name());
    validateDuplicatedNameForCreate(normalizedName);

    FlagEntity entity = new FlagEntity();
    entity.setName(normalizedName);
    entity.setErpCode(input.erpCode());
    entity.setStatus(input.status() != null && input.status() != StatusEnum.NULL ? input.status() : StatusEnum.ACTIVE);

    syncCompanies(entity, input.companyIds());
    syncAcquirers(entity, input.acquirerIds());

    return flagRepository.save(entity);
  }

  @Transactional
  public FlagEntity update(UUID flagId, FlagInput input) {
    FlagEntity entity = getById(flagId);

    validateRequiredFields(input);

    String normalizedName = normalizeName(input.name());
    validateDuplicatedNameForUpdate(flagId, normalizedName);

    entity.setName(normalizedName);
    entity.setErpCode(input.erpCode());

    if (input.status() != null && input.status() != StatusEnum.NULL) {
      entity.setStatus(input.status());
    }

    syncCompanies(entity, input.companyIds());
    syncAcquirers(entity, input.acquirerIds());

    return flagRepository.save(entity);
  }

  @Transactional
  public void activate(UUID flagId) {
    FlagEntity entity = getById(flagId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.INACTIVE && currentStatus != StatusEnum.BLOCKED) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only INACTIVE or BLOCKED flags can be activated. Current status: " + currentStatus
      );
    }
    entity.activate();
  }

  @Transactional
  public void deactivate(UUID flagId) {
    FlagEntity entity = getById(flagId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE flags can be deactivated. Current status: " + currentStatus
      );
    }
    entity.inactivate();
  }

  @Transactional
  public void block(UUID flagId) {
    FlagEntity entity = getById(flagId);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE flags can be blocked. Current status: " + currentStatus
      );
    }
    entity.block();
  }

  @Transactional
  public FlagEntity addCompaniesRelations(UUID flagId, List<UUID> companyIds) {
    FlagEntity flag = getById(flagId);

    List<UUID> ids = distinctIds(companyIds);
    List<CompanyEntity> companies = ids.isEmpty() ? List.of() : companyRepository.findAllById(ids);

    validateAllIdsFound(ids, companies.stream().map(CompanyEntity::getId).toList(), "Company");

    Set<UUID> existingIds = flag.getFlagCompanies().stream()
      .filter(item -> item.getCompany() != null)
      .map(item -> item.getCompany().getId())
      .collect(Collectors.toSet());

    for (CompanyEntity company : companies) {
      if (existingIds.contains(company.getId())) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Company already linked to this flag: " + company.getId()
        );
      }

      RelationFlagCompanyEntity relation = new RelationFlagCompanyEntity();
      relation.setFlag(flag);
      relation.setCompany(company);
      flag.getFlagCompanies().add(relation);
    }

    return flagRepository.save(flag);
  }

  @Transactional
  public FlagEntity removeCompanyRelations(UUID flagId, UUID companyId) {
    FlagEntity flag = getById(flagId);

    boolean removed = flag.getFlagCompanies().removeIf(item ->
      item.getCompany() != null && companyId.equals(item.getCompany().getId())
    );

    if (!removed) {
      throw BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Company relation not found for flagId=%s and companyId=%s".formatted(flagId, companyId)
      );
    }

    return flagRepository.save(flag);
  }

  @Transactional
  public FlagEntity addAcquirerRelations(UUID flagId, List<FlagAcquirerRelationItemInput> items) {
    FlagEntity flag = getById(flagId);

    List<UUID> ids = distinctIds(
      items.stream().map(FlagAcquirerRelationItemInput::acquirerId).toList()
    );

    List<AcquirerEntity> acquirers = ids.isEmpty() ? List.of() : acquirerRepository.findAllById(ids);

    validateAllIdsFound(ids, acquirers.stream().map(AcquirerEntity::getId).toList(), "Acquirer");

    Map<UUID, AcquirerEntity> acquirerById = acquirers.stream()
      .collect(Collectors.toMap(AcquirerEntity::getId, item -> item));

    Set<UUID> existingIds = flag.getFlagAcquirers().stream()
      .filter(item -> item.getAcquirer() != null)
      .map(item -> item.getAcquirer().getId())
      .collect(Collectors.toSet());

    for (FlagAcquirerRelationItemInput input : items) {
      UUID acquirerId = input.acquirerId();

      if (existingIds.contains(acquirerId)) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Acquirer already linked to this flag: " + acquirerId
        );
      }

      AcquirerEntity acquirer = acquirerById.get(acquirerId);
      if (acquirer == null) {
        throw BusinessException.notFound(
          ErrorCode.NOT_FOUND,
          "Acquirer not found for id " + acquirerId
        );
      }

      RelationFlagAcquirerEntity relation = new RelationFlagAcquirerEntity();
      relation.setFlag(flag);
      relation.setAcquirer(acquirer);
      relation.setAcquirerCode(input.acquirerCode().trim());
      flag.getFlagAcquirers().add(relation);
    }

    return flagRepository.save(flag);
  }

  @Transactional
  public FlagEntity removeAcquirerRelations(UUID flagId, UUID acquirerId) {
    FlagEntity flag = getById(flagId);

    boolean removed = flag.getFlagAcquirers().removeIf(item ->
      item.getAcquirer() != null && acquirerId.equals(item.getAcquirer().getId())
    );

    if (!removed) {
      throw BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Acquirer relation not found for flagId=%s and acquirerId=%s".formatted(flagId, acquirerId)
      );
    }

    return flagRepository.save(flag);
  }

  private void syncCompanies(FlagEntity flag, List<UUID> companyIds) {
    List<UUID> ids = distinctIds(companyIds);
    List<CompanyEntity> companies = ids.isEmpty() ? List.of() : companyRepository.findAllById(ids);

    validateAllIdsFound(ids, companies.stream().map(CompanyEntity::getId).toList(), "Company");

    Map<UUID, RelationFlagCompanyEntity> currentByCompanyId = flag.getFlagCompanies().stream()
      .filter(fc -> fc.getCompany() != null && fc.getCompany().getId() != null)
      .collect(Collectors.toMap(fc -> fc.getCompany().getId(), fc -> fc));

    flag.getFlagCompanies().removeIf(fc ->
      fc.getCompany() == null || fc.getCompany().getId() == null || !ids.contains(fc.getCompany().getId())
    );

    Set<UUID> existing = flag.getFlagCompanies().stream()
      .map(fc -> fc.getCompany().getId())
      .collect(Collectors.toSet());

    for (CompanyEntity company : companies) {
      if (existing.contains(company.getId())) continue;

      RelationFlagCompanyEntity relation = new RelationFlagCompanyEntity();
      relation.setFlag(flag);
      relation.setCompany(company);
      flag.getFlagCompanies().add(relation);
    }
  }

  private void syncAcquirers(FlagEntity flag, List<UUID> acquirerIds) {
    List<UUID> ids = distinctIds(acquirerIds);
    List<AcquirerEntity> acquirers = ids.isEmpty() ? List.of() : acquirerRepository.findAllById(ids);

    validateAllIdsFound(ids, acquirers.stream().map(AcquirerEntity::getId).toList(), "Acquirer");

    flag.getFlagAcquirers().removeIf(fa ->
      fa.getAcquirer() == null || fa.getAcquirer().getId() == null || !ids.contains(fa.getAcquirer().getId())
    );

    Set<UUID> existing = flag.getFlagAcquirers().stream()
      .map(fa -> fa.getAcquirer().getId())
      .collect(Collectors.toSet());

    for (AcquirerEntity acquirer : acquirers) {
      if (existing.contains(acquirer.getId())) continue;

      RelationFlagAcquirerEntity relation = new RelationFlagAcquirerEntity();
      relation.setFlag(flag);
      relation.setAcquirer(acquirer);
      relation.setAcquirerCode(null);
      flag.getFlagAcquirers().add(relation);
    }
  }

  private void validateRequiredFields(FlagInput input) {
    if (input.name() == null || input.name().trim().isBlank()) {
      throw BusinessException.badRequest(ErrorCode.VALIDATION_ERROR, "The field 'name' is required.");
    }
  }

  private void validateDuplicatedNameForCreate(String name) {
    if (flagRepository.existsByNameIgnoreCase(name)) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "There is already a flag with the same name."
      );
    }
  }

  private void validateDuplicatedNameForUpdate(UUID flagId, String name) {
    var existing = flagRepository.findByNameIgnoreCase(name);
    if (existing.isPresent() && !existing.get().getId().equals(flagId)) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "There is already a flag with the same name."
      );
    }
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

  private String normalizeName(String value) {
    return value.trim();
  }
}