package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.ReleasesBankModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.bank.ReleasesBankManualResult;
import com.cardsync.bff.controller.v1.representation.model.bank.ReleasesBankModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.ValueTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.ReleasesBankFilter;
import com.cardsync.domain.filter.ReleasesBankManualInput;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.BankingDomicileRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.domain.repository.FlagRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import com.cardsync.domain.service.support.ValueTotalsQueryService;
import com.cardsync.domain.service.support.TransactionTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.ReleasesBankSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReleasesBankService {

  private final ReleasesBankSpecs releasesBankSpecs;
  private final ValueTotalsQueryService totalsQueryService;
  private final ReleasesBankRepository releasesBankRepository;
  private final ReleasesBankModelAssembler releasesBankModelAssembler;
  private final FlagRepository flagRepository;
  private final CompanyRepository companyRepository;
  private final AcquirerRepository acquirerRepository;
  private final EstablishmentRepository establishmentRepository;
  private final BankingDomicileRepository bankingDomicileRepository;

  @Transactional(readOnly = true)
  public Page<ReleasesBankModel> search(Pageable pageable, ListQueryDto<ReleasesBankFilter> query) {
    Specification<ReleasesBankEntity> filterSpec = releasesBankSpecs.fromQueryForTotals(query);
    Specification<ReleasesBankEntity> dataSpec   = releasesBankSpecs.fromQuery(query);

    long total = releasesBankRepository.count(filterSpec);

    List<ReleasesBankModel> content = total == 0
      ? List.of()
      : releasesBankRepository.findAll(dataSpec, pageable)
      .stream()
      .map(releasesBankModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public ValueTotalsModel totals(ListQueryDto<ReleasesBankFilter> query) {
    Specification<ReleasesBankEntity> spec = releasesBankSpecs.fromQueryForTotals(query);
    return totalsQueryService.totals(ReleasesBankEntity.class, spec,"releaseValue");
  }

  @Transactional
  public ReleasesBankManualResult createManual(ReleasesBankManualInput input) {
    var company = companyRepository.findById(input.companyId())
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.COMPANY_NOT_FOUND, "Company not found: " + input.companyId()));

    var bankingDomicile = bankingDomicileRepository.findById(input.bankingDomicileId())
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.BANKING_DOMICILE_NOT_FOUND, "Banking domicile not found: " + input.bankingDomicileId()));

    var entity = new ReleasesBankEntity();
    entity.setCompany(company);
    entity.setBankingDomicile(bankingDomicile);
    entity.setBank(bankingDomicile.getBank());
    entity.setReleaseDate(input.releaseDate());
    entity.setReleaseValue(input.releaseValue());
    entity.setReleaseCategory(input.releaseCategory());
    entity.setReleaseCategoryCode(input.releaseCategory() != null ? input.releaseCategory().getCode() : null);
    entity.setModalityPaymentBank(input.modalityPaymentBank());
    entity.setReconciliationStatus(StatusPaymentBankEnum.PENDING);
    entity.setNumberReconciliations(0);
    entity.setDescriptionHistoricalBank(input.description());
    entity.setDocumentComplementNumber(input.document());
    entity.setHistoricalCodeBank(input.historicalCodeBank());

    if (input.acquirerId() != null) {
      var acquirer = acquirerRepository.findById(input.acquirerId())
        .orElseThrow(() -> BusinessException.notFound(ErrorCode.ACQUIRER_NOT_FOUND, "Acquirer not found: " + input.acquirerId()));
      entity.setAcquirer(acquirer);
    }

    if (input.establishmentId() != null) {
      var establishment = establishmentRepository.findById(input.establishmentId())
        .orElseThrow(() -> BusinessException.notFound(ErrorCode.ESTABLISHMENT_NOT_FOUND, "Establishment not found: " + input.establishmentId()));
      entity.setEstablishment(establishment);
    }

    if (input.flagId() != null) {
      var flag = flagRepository.findById(input.flagId())
        .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "Flag not found: " + input.flagId()));
      entity.setFlag(flag);
    }

    var saved = releasesBankRepository.save(entity);
    return new ReleasesBankManualResult(saved.getId());
  }

  @Transactional
  public void deleteManual(java.util.UUID id) {
    var release = releasesBankRepository.findById(id)
      .orElseThrow(() -> BusinessException.notFound(ErrorCode.NOT_FOUND, "Release not found: " + id));

    if (release.getProcessedFile() != null) {
      throw BusinessException.badRequest(ErrorCode.BUSINESS_ERROR, "Only manual releases can be deleted");
    }

    releasesBankRepository.delete(release);
  }

}