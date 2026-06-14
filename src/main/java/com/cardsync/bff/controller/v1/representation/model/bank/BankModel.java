package com.cardsync.bff.controller.v1.representation.model.bank;

import com.cardsync.domain.model.enums.StatusEnum;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class BankModel extends RepresentationModel<BankModel> {

  private UUID id;
  private String code;
  private String name;
  private StatusEnum status;
  private OffsetDateTime statusDate;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}