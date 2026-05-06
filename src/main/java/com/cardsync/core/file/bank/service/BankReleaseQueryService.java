package com.cardsync.core.file.bank.service;

import com.cardsync.bff.controller.v1.representation.model.bank.BankReleaseModel;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankReleaseQueryService {

  private final ReleasesBankRepository releasesBankRepository;

  @Transactional(readOnly = true)
  public Page<BankReleaseModel> list(Pageable pageable) {
    return releasesBankRepository.findAll(pageable).map(this::toModel);
  }

  private BankReleaseModel toModel(ReleasesBankEntity entity) {
    BankingDomicileEntity domicile = entity.getBankingDomicile();

    return new BankReleaseModel(
      entity.getId(),
      entity.getProcessedFile() != null ? entity.getProcessedFile().getFile() : null,
      entity.getLineNumber(),
      entity.getBank() != null ? entity.getBank().getName() : null,
      entity.getBank() != null ? entity.getBank().getCode() : null,
      entity.getCompany() != null ? entity.getCompany().getFantasyName() : null,
      entity.getCompany() != null ? entity.getCompany().getCnpj() : null,
      entity.getAcquirer() != null ? entity.getAcquirer().getFantasyName() : null,
      entity.getEstablishment() != null ? String.valueOf(entity.getEstablishment().getPvNumber()) : null,
      entity.getEstablishment() != null ? entity.getEstablishment().getPvNumber() : null,
      entity.getFlag() != null ? entity.getFlag().getName() : null,
      domicileName(domicile),
      domicile != null ? domicile.getAgency() : null,
      domicile != null ? domicile.getCurrentAccount() : null,
      entity.getReleaseDate(),
      entity.getAccountingDate(),
      entity.getReleaseValue(),
      entity.getReconciliationStatus(),
      entity.getModalityPaymentBank(),
      entity.getHistoricalCodeBank(),
      entity.getReleaseCategoryCode(),
      entity.getDescriptionHistoricalBank(),
      entity.getDocumentComplementNumber(),
      entity.getComplementRelease(),
      entity.getNumberReconciliations(),
      entity.getNumberCreditOrders(),
      entity.getNumberParcels()
    );
  }

  private String domicileName(BankingDomicileEntity domicile) {
    if (domicile == null) return null;
    String bank = domicile.getBank() != null ? domicile.getBank().getName() : null;
    String agency = domicile.getAgency() != null ? String.valueOf(domicile.getAgency()) : null;
    String account = domicile.getCurrentAccount() != null ? String.valueOf(domicile.getCurrentAccount()) : null;
    String accountDigit = domicile.getAccountDigit();

    String accountText = account == null ? null : account + (accountDigit == null || accountDigit.isBlank() ? "" : "-" + accountDigit);
    return String.join(" / ", java.util.stream.Stream.of(bank, agency, accountText)
      .filter(value -> value != null && !value.isBlank())
      .toList());
  }
}
