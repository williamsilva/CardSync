package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.BankingDomicileInput;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.BankingDomicileFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.BankingDomicileRepository;
import com.cardsync.infrastructure.repository.spec.BankingDomicileSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BankingDomicileService {

  private final BankService bankService;
  private final CompanyService companyService;
  private final BankingDomicileSpecs bankingDomicileSpecs;
  private final BankingDomicileRepository bankingDomicileRepository;

  @Transactional(readOnly = true)
  public BankingDomicileEntity getById(UUID id) {
    return bankingDomicileRepository.findById(id)
      .orElseThrow(() -> BusinessException.notFound(
        ErrorCode.BANKING_DOMICILE_NOT_FOUND,
        "Banking domicile not found for id " + id
      ));
  }

  @Transactional(readOnly = true)
  public List<BankingDomicileEntity> listOptionsFilter() {
    return bankingDomicileRepository
      .findAll(Sort.by(Sort.Direction.ASC, "company", "bank"));
  }

  @Transactional(readOnly = true)
  public Page<BankingDomicileEntity> search(Pageable pageable, ListQueryDto<BankingDomicileFilter> query) {
    var spec = bankingDomicileSpecs.fromQuery(query);
    return bankingDomicileRepository.findAll(spec, pageable);
  }

  @Transactional
  public BankingDomicileEntity create(BankingDomicileInput input) {
    validateInput(input);

    BankEntity bank = bankService.getById(input.bankId());
    CompanyEntity company = companyService.getById(input.companyId());

    validateDuplicate(null, input);

    BankingDomicileEntity entity = new BankingDomicileEntity();
    applyInput(entity, input, bank, company);
    entity.setStatus(StatusEnum.ACTIVE);

    return bankingDomicileRepository.save(entity);
  }

  @Transactional
  public BankingDomicileEntity update(UUID id, BankingDomicileInput input) {
    BankingDomicileEntity entity = getById(id);
    validateInput(input);

    BankEntity bank = bankService.getById(input.bankId());
    CompanyEntity company = companyService.getById(input.companyId());

    validateDuplicate(id, input);
    applyInput(entity, input, bank, company);

    if (input.status() != null && input.status() != StatusEnum.NULL) {
      entity.setStatus(input.status());
    }

    return bankingDomicileRepository.save(entity);
  }

  @Transactional
  public void activate(UUID id) {
    BankingDomicileEntity entity = getById(id);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.INACTIVE && currentStatus != StatusEnum.BLOCKED) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only INACTIVE or BLOCKED banking domiciles can be activated. Current status: " + currentStatus
      );
    }

    entity.activate();
  }

  @Transactional
  public void deactivate(UUID id) {
    BankingDomicileEntity entity = getById(id);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE banking domiciles can be deactivated. Current status: " + currentStatus
      );
    }

    entity.inactivate();
  }

  @Transactional
  public void block(UUID id) {
    BankingDomicileEntity entity = getById(id);
    StatusEnum currentStatus = entity.getStatus();

    if (currentStatus != StatusEnum.ACTIVE) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "Only ACTIVE banking domiciles can be blocked. Current status: " + currentStatus
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
          "Only INACTIVE or BLOCKED banking domiciles can be activated. Banking domicile id: " + entity.getId()
        );
      }
      entity.activate();
    });
  }

  @Transactional
  public void deactivateBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      StatusEnum currentStatus = entity.getStatus();
      if (currentStatus != StatusEnum.ACTIVE) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only ACTIVE banking domiciles can be deactivated. Banking domicile id: " + entity.getId()
        );
      }
      entity.inactivate();
    });
  }

  @Transactional
  public void blockBulk(List<UUID> ids) {
    updateBulkStatus(ids, entity -> {
      StatusEnum currentStatus = entity.getStatus();
      if (currentStatus != StatusEnum.ACTIVE) {
        throw BusinessException.badRequest(
          ErrorCode.VALIDATION_ERROR,
          "Only ACTIVE banking domiciles can be blocked. Banking domicile id: " + entity.getId()
        );
      }
      entity.block();
    });
  }

  private void updateBulkStatus(List<UUID> ids, Consumer<BankingDomicileEntity> updater) {
    if (ids == null || ids.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of banking domicile ids must not be empty."
      );
    }

    List<UUID> distinctIds = ids.stream()
      .filter(Objects::nonNull)
      .distinct()
      .toList();

    if (distinctIds.isEmpty()) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The list of banking domicile ids must not be empty."
      );
    }

    List<BankingDomicileEntity> entities = bankingDomicileRepository.findAllById(distinctIds);
    Map<UUID, BankingDomicileEntity> byId = entities.stream()
      .collect(Collectors.toMap(BankingDomicileEntity::getId, entity -> entity));

    List<UUID> missingIds = distinctIds.stream()
      .filter(id -> !byId.containsKey(id))
      .toList();

    if (!missingIds.isEmpty()) {
      throw BusinessException.notFound(
        ErrorCode.BANKING_DOMICILE_NOT_FOUND,
        "Banking domicile not found for ids " + missingIds
      );
    }

    entities.forEach(updater);
    bankingDomicileRepository.saveAll(entities);
  }

  private void validateInput(BankingDomicileInput input) {
    if (input.accountClosingDate().isBefore(input.accountOpeningDate())) {
      throw BusinessException.badRequest(
        ErrorCode.VALIDATION_ERROR,
        "The field 'accountClosingDate' must be equal to or after 'accountOpeningDate'."
      );
    }
  }

  private void validateDuplicate(UUID currentId, BankingDomicileInput input) {
    String agencyDigit = normalizeText(input.agencyDigit());
    String accountDigit = normalizeText(input.accountDigit());

    bankingDomicileRepository
      .findDuplicate(
        input.bankId(),
        input.companyId(),
        input.agency(),
        input.currentAccount(),
        agencyDigit,
        accountDigit
      )
      .filter(existing -> currentId == null || !existing.getId().equals(currentId))
      .ifPresent(existing -> {
        throw BusinessException.badRequest(
          ErrorCode.BANKING_DOMICILE_ALREADY_EXISTS,
          "There is already a banking domicile registered for this bank, company, agency and account."
        );
      });
  }

  private void applyInput(
    BankingDomicileEntity entity,
    BankingDomicileInput input,
    BankEntity bank,
    CompanyEntity company
  ) {
    entity.setAgency(input.agency());
    entity.setAgencyDigit(normalizeText(input.agencyDigit()));
    entity.setCurrentAccount(input.currentAccount());
    entity.setAccountDigit(normalizeText(input.accountDigit()));
    entity.setAccountOpeningDate(input.accountOpeningDate());
    entity.setAccountClosingDate(input.accountClosingDate());
    entity.setExpectsFile(input.expectsFile());
    entity.setBank(bank);
    entity.setCompany(company);
  }

  private String normalizeText(String value) {
    return value == null ? "" : value.trim();
  }
}
