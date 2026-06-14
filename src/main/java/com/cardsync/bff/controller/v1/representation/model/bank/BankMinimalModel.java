package com.cardsync.bff.controller.v1.representation.model.bank;

import com.cardsync.domain.model.enums.StatusEnum;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class BankMinimalModel extends RepresentationModel<BankMinimalModel> {

  private UUID id;
  private String name;
  private String code;

  private StatusEnum status;
}
