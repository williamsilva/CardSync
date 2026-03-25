package com.cardsync.bff.controller.v1.representation.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class EmailLogModel extends RepresentationModel<EmailLogModel> {

  private UUID id;
  private String status;
  private String eventType;

  private String subject;
  private String template;
  private String recipient;
  private String errorMessage;

  private OffsetDateTime sentAt;

}
