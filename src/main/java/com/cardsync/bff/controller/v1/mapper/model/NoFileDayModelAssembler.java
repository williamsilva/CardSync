package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.NoFileDayController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bank.BankMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.nofileday.NoFileDayModel;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.BankEntity;
import com.cardsync.domain.model.NoFileDayEntity;
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
    model.setDescription(entity.getDescription());
    model.setDayType(entity.getDayType());
    model.setFileGroup(entity.getFileGroup());
    model.setStatus(entity.getStatus());
    model.setNoFileDate(entity.getNoFileDate());
    model.setStatusDate(entity.getStatusDate());
    model.setCreatedAt(entity.getCreatedAt());
    model.setUpdatedAt(entity.getUpdatedAt());

    model.setBank(toBank(entity.getBank()));
    model.setAcquirer(toAcquirer(entity.getAcquirer()));

    return model;
  }

  private AcquirerMinimalModel toAcquirer(AcquirerEntity entity) {
    if (entity == null) {
      return null;
    }
    return AcquirerMinimalModel.builder()
      .id(entity.getId())
      .cnpj(entity.getCnpj())
      .fantasyName(entity.getFantasyName())
      .socialReason(entity.getSocialReason())
      .status(entity.getStatus() == null ? null : entity.getStatus().name())
      .build();
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
}