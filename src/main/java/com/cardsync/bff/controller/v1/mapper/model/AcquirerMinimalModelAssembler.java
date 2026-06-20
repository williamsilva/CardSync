package com.cardsync.bff.controller.v1.mapper.model;

import com.cardsync.bff.controller.v1.AcquirerController;
import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.domain.model.AcquirerEntity;
import lombok.NonNull;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.stereotype.Component;

@Component
public class AcquirerMinimalModelAssembler extends RepresentationModelAssemblerSupport<AcquirerEntity, @NonNull AcquirerMinimalModel> {

  @Autowired
  private ModelMapper modelMapper;

  public AcquirerMinimalModelAssembler() {
    super(AcquirerController.class, AcquirerMinimalModel.class);
  }

  @Override
  public AcquirerMinimalModel toModel(@NonNull AcquirerEntity entity) {
    AcquirerMinimalModel model = createModelWithId(entity.getId(), entity);
    modelMapper.map(entity, model);

    return model;
  }

}