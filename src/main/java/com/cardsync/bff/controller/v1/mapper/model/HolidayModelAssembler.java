package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.HolidayController;
import com.cardsync.bff.controller.v1.representation.model.holiday.HolidayModel;
import com.cardsync.domain.model.HolidayEntity;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class HolidayModelAssembler extends RepresentationModelAssemblerSupport<HolidayEntity, HolidayModel> {

  private static final ThreadLocal<Integer> targetYear = new ThreadLocal<>();

  @Autowired
  private ModelMapper modelMapper;

  public HolidayModelAssembler() {
    super(HolidayController.class, HolidayModel.class);
  }

  public void setTargetYear(int year) {
    targetYear.set(year);
  }

  public void clearTargetYear() {
    targetYear.remove();
  }

  @Override
  public HolidayModel toModel(HolidayEntity entity) {
    HolidayModel model = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, model);

    if (Boolean.TRUE.equals(entity.getRecurring()) && model.getHolidayDate() != null) {
      int year = targetYear.get() != null ? targetYear.get() : LocalDate.now().getYear();
      model.setHolidayDate(model.getHolidayDate().withYear(year));
    }

    return model;
  }

  @Override
  public CollectionModel<HolidayModel> toCollectionModel(Iterable<? extends HolidayEntity> entities) {
    return super.toCollectionModel(entities);
  }
}