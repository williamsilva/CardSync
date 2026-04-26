package com.cardsync.domain.service;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.enums.StatusEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.domain.repository.FlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractLookupService {

  private final FlagRepository flagRepository;
  private final CompanyRepository companyRepository;
  private final AcquirerRepository acquirerRepository;
  private final EstablishmentRepository establishmentRepository;

  @Transactional(readOnly = true)
  public List<AcquirerEntity> listAcquirersByCompany(UUID companyId) {
    ensureCompanyExists(companyId);

    return acquirerRepository.findAllByCompanyId(companyId).stream()
      .filter(entity -> entity.getStatus() == StatusEnum.ACTIVE)
      .toList();
  }

  @Transactional(readOnly = true)
  public List<EstablishmentEntity> listEstablishmentsByCompanyAndAcquirer(UUID companyId, UUID acquirerId) {
    ensureCompanyExists(companyId);
    ensureAcquirerExists(acquirerId);

    return establishmentRepository.findByCompany_IdAndAcquirer_IdOrderByPvNumberAsc(companyId, acquirerId)
      .stream()
      .filter(entity -> entity.getStatus() == StatusEnum.ACTIVE)
      .toList();
  }

  @Transactional(readOnly = true)
  public List<FlagEntity> listFlagsByCompanyAndAcquirer(UUID companyId, UUID acquirerId) {
    ensureCompanyExists(companyId);
    ensureAcquirerExists(acquirerId);

    return flagRepository.findAllByCompanyIdAndAcquirerId(companyId, acquirerId).stream()
      .filter(entity -> entity.getStatus() == StatusEnum.ACTIVE)
      .toList();
  }

  private void ensureCompanyExists(UUID companyId) {
    boolean exists = companyRepository.existsById(companyId);
    if (!exists) {
      throw BusinessException.notFound(
        ErrorCode.COMPANY_NOT_FOUND,
        "Company not found for id " + companyId
      );
    }
  }

  private void ensureAcquirerExists(UUID acquirerId) {
    boolean exists = acquirerRepository.existsById(acquirerId);
    if (!exists) {
      throw BusinessException.notFound(
        ErrorCode.ACQUIRER_NOT_FOUND,
        "Acquirer not found for id " + acquirerId
      );
    }
  }
}
