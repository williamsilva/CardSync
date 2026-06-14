package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.BankingDomicileController;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileModel;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class BankingDomicileModelAssembler extends RepresentationModelAssemblerSupport<BankingDomicileEntity, BankingDomicileModel> {

  public BankingDomicileModelAssembler() {
    super(BankingDomicileController.class, BankingDomicileModel.class);
  }

  @Override
  public BankingDomicileModel toModel(BankingDomicileEntity entity) {
    BankingDomicileModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setStatus(entity.getStatus());
    model.setStatusDate(entity.getStatusDate());
    model.setAccountOpeningDate(entity.getAccountOpeningDate());
    model.setAccountClosingDate(entity.getAccountClosingDate());
    model.setExpectsFile(entity.getExpectsFile());
    model.setAgency(entity.getAgency());
    model.setCreatedAt(entity.getCreatedAt());
    model.setUpdatedAt(entity.getUpdatedAt());
    model.setAgencyDigit(entity.getAgencyDigit());
    model.setAccountDigit(entity.getAccountDigit());
    model.setCurrentAccount(entity.getCurrentAccount());

    model.setBank(toBank(entity.getBank()));
    model.setCompany(toCompany(entity.getCompany()));

    return model;
  }

  private static BankMinimalModel toBank(BankEntity entity) {
    if (entity == null) {
      return null;
    }

    return BankMinimalModel.builder()
      .id(entity.getId())
      .name(entity.getName())
      .code(entity.getCode())
      .build();
  }

  private CompanyMinimalModel toCompany(CompanyEntity entity) {
    if (entity == null) {
      return null;
    }
    return CompanyMinimalModel.builder()
      .id(entity.getId())
      .cnpj(entity.getCnpj())
      .fantasyName(entity.getFantasyName())
      .socialReason(entity.getSocialReason())
      .type(entity.getType() == null ? null : entity.getType().name())
      .status(entity.getStatus() == null ? null : entity.getStatus().name())
      .build();
  }

}