package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.ContractFlagInput;
import com.cardsync.bff.controller.v1.representation.input.ContractInput;
import com.cardsync.bff.controller.v1.representation.input.ContractRateInput;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.ContractFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.ContractFlagEntity;
import com.cardsync.domain.model.ContractRateEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.RelationAcquirerCompanyEntity;
import com.cardsync.domain.model.enums.ContractEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.ContractRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.domain.repository.FlagRepository;
import com.cardsync.infrastructure.repository.spec.ContractSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractService {

  private final ContractSpecs contractSpecs;
  private final FlagRepository flagRepository;
  private final CompanyRepository companyRepository;
  private final ContractRepository contractRepository;
  private final AcquirerRepository acquirerRepository;
  private final EstablishmentRepository establishmentRepository;

  @Transactional(readOnly = true)
  public ContractEntity getById(UUID contractId) {
    return contractRepository.findDetailedById(contractId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.CONTRACT_NOT_FOUND,
        "Contract not found for id " + contractId
      ));
  }

  @Transactional(readOnly = true)
  public Page<ContractEntity> list(Pageable pageable, ListQueryDto<ContractFilter> query) {
    Specification<ContractEntity> spec = contractSpecs.fromQuery(query);
    return contractRepository.findAll(spec, pageable);
  }

  @Transactional
  public ContractEntity create(ContractInput input) {
    validateRequiredFields(input);

    AcquirerEntity acquirer = getAcquirerById(input.acquirerId());
    CompanyEntity company = resolveCompany(input.companyId(), input.establishmentId());
    EstablishmentEntity establishment = resolveEstablishment(input.establishmentId());

    validateReferencesConsistency(company, acquirer, establishment);
    validateContractFlagsContext(company, acquirer, input.contractFlags());
    validateDuplicate( company, acquirer, establishment, null);

    ContractEntity entity = new ContractEntity();
    entity.setDescription(input.description().trim());
    entity.setStartDate(input.startDate());
    entity.setEndDate(input.endDate());
    entity.setCompany(company);
    entity.setAcquirer(acquirer);
    entity.setEstablishment(establishment);
    entity.setStatus(input.status() != null && input.status() != ContractEnum.NULL ? input.status() : ContractEnum.VALIDITY);

    syncFlags(entity, input.contractFlags());
    ContractEntity saved = contractRepository.save(entity);
    return getById(saved.getId());
  }

  @Transactional
  public ContractEntity update(UUID contractId, ContractInput input) {
    ContractEntity entity = getById(contractId);
    validateRequiredFields(input);

    AcquirerEntity acquirer = getAcquirerById(input.acquirerId());
    CompanyEntity company = resolveCompany(input.companyId(), input.establishmentId());
    EstablishmentEntity establishment = resolveEstablishment(input.establishmentId());

    validateReferencesConsistency(company, acquirer, establishment);
    validateContractFlagsContext(company, acquirer, input.contractFlags());
    validateDuplicate(company, acquirer, establishment, contractId);

    entity.setDescription(input.description().trim());
    entity.setStartDate(input.startDate());
    entity.setEndDate(input.endDate());
    entity.setCompany(company);
    entity.setAcquirer(acquirer);
    entity.setEstablishment(establishment);

    if (input.status() != null && input.status() != ContractEnum.NULL) {
      entity.setStatus(input.status());
    }
    replaceFlags(entity, input.contractFlags());

    ContractEntity saved = contractRepository.save(entity);
    return getById(saved.getId());
  }

  @Transactional
  public void delete(UUID contractId) {
    ContractEntity entity = getById(contractId);
    contractRepository.delete(entity);
  }

  @Transactional
  public void validity(UUID contractId) {
    ContractEntity entity = getById(contractId);
    ContractEnum currentStatus = entity.getStatus();

    if (currentStatus != ContractEnum.EXPIRED && currentStatus != ContractEnum.CLOSED) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only EXPIRED or CLOSED contracts can be validity. Current status: " + currentStatus
      );
    }

    validateDuplicate(
      entity.getCompany(),
      entity.getAcquirer(),
      entity.getEstablishment(),
      entity.getId()
    );
    entity.validity();
  }

  @Transactional
  public void expired(UUID contractId) {
    ContractEntity entity = getById(contractId);
    ContractEnum currentStatus = entity.getStatus();

    if (currentStatus != ContractEnum.VALIDITY) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only VALIDITY contracts can be expired. Current status: " + currentStatus
      );
    }
    entity.expired();
  }

  @Transactional
  public void closed(UUID contractId) {
    ContractEntity entity = getById(contractId);
    ContractEnum currentStatus = entity.getStatus();

    if (currentStatus != ContractEnum.VALIDITY) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only VALIDITY contracts can be closed. Current status: " + currentStatus
      );
    }
    entity.closed();
  }

  @Transactional
  public void validityBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      ContractEnum currentStatus = entity.getStatus();

      if (currentStatus != ContractEnum.EXPIRED && currentStatus != ContractEnum.CLOSED) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only EXPIRED or CLOSED contracts can be validity. Contract id: " + entity.getId()
        );
      }

      validateDuplicate(
        entity.getCompany(),
        entity.getAcquirer(),
        entity.getEstablishment(),
        entity.getId()
      );

      entity.setStatus(ContractEnum.VALIDITY);
    });
  }

  @Transactional
  public void expiredBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      ContractEnum currentStatus = entity.getStatus();
      if (currentStatus != ContractEnum.VALIDITY) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only VALIDITY contracts can be expired. Contract id: " + entity.getId()
        );
      }
      entity.setStatus(ContractEnum.EXPIRED);
    });
  }

  @Transactional
  public void closedBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      ContractEnum currentStatus = entity.getStatus();
      if (currentStatus != ContractEnum.CLOSED) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only VALIDITY contracts can be closed. Contract id: " + entity.getId()
        );
      }
      entity.setStatus(ContractEnum.CLOSED);
    });
  }

  private void replaceFlags(ContractEntity contract, List<ContractFlagInput> inputs) {
    if (contract.getId() != null && !contract.getContractFlags().isEmpty()) {
      contract.getContractFlags().forEach(contractFlag -> {
        contractFlag.getContractRates().clear();
        contractFlag.setContract(null);
      });

      contract.getContractFlags().clear();
      contractRepository.flush();
    }

    syncFlags(contract, inputs);
  }

  private void updateBulkStatus(List<UUID> ids, Consumer<ContractEntity> updater) {
    if (ids == null || ids.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of contract ids must not be empty."
      );
    }

    List<UUID> distinctIds = ids.stream()
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    if (distinctIds.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of contract ids must not be empty."
      );
    }

    List<ContractEntity> entities = contractRepository.findAllById(distinctIds);

    Map<UUID, ContractEntity> byId = entities.stream()
      .collect(Collectors.toMap(ContractEntity::getId, e -> e));

    List<UUID> missingIds = distinctIds.stream()
      .filter(id -> !byId.containsKey(id))
      .toList();

    if (!missingIds.isEmpty()) {
      throw BusinessException.notFound(
        ErrorCode.CONTRACT_NOT_FOUND,
        "Contract not found for ids " + missingIds
      );
    }

    entities.forEach(updater);
    contractRepository.saveAll(entities);
  }

  private void validateRequiredFields(ContractInput input) {
    if (input.description() == null || input.description().isBlank()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'description' is required."
      );
    }

    if (input.startDate() == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'startDate' is required."
      );
    }

    if (input.acquirerId() == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'acquirerId' is required."
      );
    }

    if (input.endDate() != null && input.endDate().isBefore(input.startDate())) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'endDate' must be greater than or equal to 'startDate'."
      );
    }

    validateFlags(input.contractFlags());
  }

  private void validateFlags(List<ContractFlagInput> contractFlags) {
    if (contractFlags == null || contractFlags.isEmpty()) {
      return;
    }

    Set<UUID> uniqueFlagIds = new HashSet<>();

    for (ContractFlagInput flagInput : contractFlags) {
      if (flagInput == null || flagInput.flagId() == null) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Every contract flag must have a valid 'flagId'."
        );
      }

      if (!uniqueFlagIds.add(flagInput.flagId())) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Duplicate flag in contractFlags: " + flagInput.flagId()
        );
      }

      validateRates(flagInput.flagId(), flagInput.contractRates());
    }
  }

  private void validateRates(UUID flagId, List<ContractRateInput> rates) {
    if (rates == null || rates.isEmpty()) {
      return;
    }

    Set<ModalityEnum> modalities = new HashSet<>();

    for (ContractRateInput rateInput : rates) {
      if (rateInput == null || rateInput.modality() == null || rateInput.modality() == ModalityEnum.NULL) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Every contract rate must have a valid 'modality'. Flag id: " + flagId
        );
      }

      if (!modalities.add(rateInput.modality())) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Duplicate modality '%s' for flag %s".formatted(rateInput.modality(), flagId)
        );
      }

      validateNonNegative(rateInput.rate(), "rate");
      validateNonNegative(rateInput.rateEcommerce(), "rateEcommerce");
      validateNonNegative(rateInput.paymentTermDays(), "paymentTermDays");
      validateNonNegative(rateInput.paymentTermDaysEcommerce(), "paymentTermDaysEcommerce");
    }
  }

  private void validateNonNegative(BigDecimal value, String field) {
    if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field '%s' must be greater than or equal to zero.".formatted(field)
      );
    }
  }

  private void validateNonNegative(Integer value, String field) {
    if (value != null && value < 0) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field '%s' must be greater than or equal to zero.".formatted(field)
      );
    }
  }

  private void validateReferencesConsistency(
    CompanyEntity company, AcquirerEntity acquirer, EstablishmentEntity establishment) {
    validateCompanyAcquirerRelation(company, acquirer);

    if (establishment == null) {
      return;
    }

    if (company != null && establishment.getCompany() != null
      && !Objects.equals(establishment.getCompany().getId(), company.getId())) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The establishment does not belong to the informed company."
      );
    }

    if (establishment.getAcquirer() != null
      && !Objects.equals(establishment.getAcquirer().getId(), acquirer.getId())) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The establishment does not belong to the informed acquirer."
      );
    }
  }

  private void validateCompanyAcquirerRelation(CompanyEntity company, AcquirerEntity acquirer) {
    if (company == null || acquirer == null) {
      return;
    }

    boolean linked = acquirer.getAcquirerCompanies().stream()
      .map(RelationAcquirerCompanyEntity::getCompany)
      .filter(Objects::nonNull)
      .anyMatch(item -> Objects.equals(item.getId(), company.getId()));

    if (!linked) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The acquirer is not linked to the informed company."
      );
    }
  }

  private void validateContractFlagsContext(
    CompanyEntity company, AcquirerEntity acquirer, List<ContractFlagInput> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return;
    }

    if (company == null) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'companyId' is required when contract flags are informed."
      );
    }

    Set<UUID> allowedFlagIds = flagRepository.findAllByCompanyIdAndAcquirerId(company.getId(), acquirer.getId())
      .stream()
      .map(FlagEntity::getId)
      .collect(Collectors.toSet());

    for (ContractFlagInput input : inputs) {
      if (input == null || input.flagId() == null) {
        continue;
      }

      if (!allowedFlagIds.contains(input.flagId())) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "The flag %s is not linked to the informed company/acquirer context.".formatted(input.flagId())
        );
      }
    }
  }

  private void validateDuplicate(CompanyEntity company,
    AcquirerEntity acquirer, EstablishmentEntity establishment,  UUID currentId) {
    boolean exists = contractRepository.existsDuplicate(
      company != null ? company.getId() : null,
      acquirer.getId(),
      establishment != null ? establishment.getId() : null,
      currentId, ContractEnum.VALIDITY.getCode()
    );

    if (exists) {
      throw BusinessException.badRequest(
        ErrorCode.CONTRACT_ALREADY_EXISTS,
        "There is already an active contract for the informed company, acquirer, and establishment."
      );
    }
  }

  private void syncFlags(ContractEntity contract, List<ContractFlagInput> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return;
    }

    List<UUID> flagIds = inputs.stream()
      .map(ContractFlagInput::flagId)
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    Map<UUID, FlagEntity> flagsById = flagRepository.findAllById(flagIds).stream()
      .collect(Collectors.toMap(FlagEntity::getId, flag -> flag));

    List<UUID> missingFlags = flagIds.stream()
      .filter(id -> !flagsById.containsKey(id))
      .toList();

    if (!missingFlags.isEmpty()) {
      throw BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Flag not found for ids " + missingFlags
      );
    }

    for (ContractFlagInput input : inputs) {
      ContractFlagEntity contractFlag = new ContractFlagEntity();
      contractFlag.setContract(contract);
      contractFlag.setFlag(flagsById.get(input.flagId()));

      syncRates(contractFlag, input.contractRates());
      contract.getContractFlags().add(contractFlag);
    }
  }

  private void syncRates(ContractFlagEntity contractFlag, List<ContractRateInput> inputs) {
    contractFlag.getContractRates().clear();

    if (inputs == null || inputs.isEmpty()) {
      return;
    }

    for (ContractRateInput input : inputs) {
      ContractRateEntity rate = new ContractRateEntity();
      rate.setContractFlag(contractFlag);
      rate.setModality(input.modality());
      rate.setRate(input.rate());
      rate.setPaymentTermDays(input.paymentTermDays());
      rate.setRateEcommerce(input.rateEcommerce() != null ? input.rateEcommerce() : BigDecimal.ZERO);
      rate.setPaymentTermDaysEcommerce(input.paymentTermDaysEcommerce() != null ? input.paymentTermDaysEcommerce() : 0);
      contractFlag.getContractRates().add(rate);
    }
  }

  private CompanyEntity resolveCompany(UUID companyId, UUID establishmentId) {
    if (companyId != null) {
      return getCompanyById(companyId);
    }

    if (establishmentId != null) {
      EstablishmentEntity establishment = resolveEstablishment(establishmentId);
      if (establishment.getCompany() != null) {
        return establishment.getCompany();
      }
    }

    return null;
  }

  private EstablishmentEntity resolveEstablishment(UUID establishmentId) {
    if (establishmentId == null) {
      return null;
    }

    return establishmentRepository.findById(establishmentId)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.NOT_FOUND,
        "Establishment not found for id " + establishmentId
      ));
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
}
