package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.HolidayController;
import com.cardsync.bff.controller.v1.representation.model.holiday.HolidayModel;
import com.cardsync.domain.model.HolidayEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class HolidayModelAssembler extends RepresentationModelAssemblerSupport<HolidayEntity, HolidayModel> {

  @Autowired
  private ModelMapper modelMapper;

  public HolidayModelAssembler() {
    super(HolidayController.class, HolidayModel.class);
  }

  @Override
  public HolidayModel toModel(HolidayEntity entity) {
    HolidayModel model = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, model);

    return model;
  }

  @Override
  public CollectionModel<HolidayModel> toCollectionModel(Iterable<? extends HolidayEntity> entities) {
    CollectionModel<HolidayModel> collectionModel = super.toCollectionModel(entities);

    return collectionModel;
  }
}