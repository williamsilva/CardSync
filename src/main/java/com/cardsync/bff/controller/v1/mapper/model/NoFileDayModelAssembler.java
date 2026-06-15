package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.NoFileDayController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.nofileday.NoFileDayModel;
import com.cardsync.domain.model.*;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class NoFileDayModelAssembler extends RepresentationModelAssemblerSupport<NoFileDayEntity, NoFileDayModel> {

  public NoFileDayModelAssembler() {
    super(NoFileDayController.class, NoFileDayModel.class);
  }

  @Override
  public NoFileDayModel toModel(NoFileDayEntity entity) {
    NoFileDayModel model = createModelWithId(entity.getId(), entity);

    model.setId(entity.getId());
    model.setStatus(entity.getStatus());
    model.setDayType(entity.getDayType());
    model.setFileGroup(entity.getFileGroup());
    model.setUpdatedAt(entity.getUpdatedAt());
    model.setCreatedAt(entity.getCreatedAt());
    model.setNoFileDate(entity.getNoFileDate());
    model.setStatusDate(entity.getStatusDate());
    model.setDescription(entity.getDescription());
    model.setAcquirerFileType(entity.getAcquirerFileType());

    model.setAcquirer(toAcquirer(entity.getAcquirer()));
    model.setBankingDomicile(toBankingDomicile(entity.getBankingDomicile()));

    return model;
  }

  private BankingDomicileMinimalModel toBankingDomicile(BankingDomicileEntity entity) {
    if (entity == null) return null;

    return BankingDomicileMinimalModel.builder()
      .id(entity.getId())
      .agency(entity.getAgency())
      .statusDate(entity.getStatusDate())
      .agencyDigit(entity.getAgencyDigit())
      .accountDigit(entity.getAccountDigit())
      .currentAccount(entity.getCurrentAccount())

      .bank(toBank(entity.getBank()))
      .company(toCompany(entity.getCompany()))
      .build();
  }

  private CompanyMinimalModel toCompany(CompanyEntity entity) {
    if (entity == null) return null;

   return CompanyMinimalModel.builder()
      .id(entity.getId())
      .fantasyName(entity.getFantasyName())
      .cnpj(entity.getCnpj())
      .build();
  }

  private BankMinimalModel toBank(BankEntity entity) {
    if (entity == null) return null;

    return BankMinimalModel.builder()
      .id(entity.getId())
      .name(entity.getName())
      .code(entity.getCode())
      .build();
  }

  private AcquirerMinimalModel toAcquirer(AcquirerEntity entity) {
    if (entity == null) return null;

    return AcquirerMinimalModel.builder()
      .id(entity.getId())
      .cnpj(entity.getCnpj())
      .fantasyName(entity.getFantasyName())
      .socialReason(entity.getSocialReason())
      .status(entity.getStatus() == null ? null : entity.getStatus().name())
      .build();
  }
}
