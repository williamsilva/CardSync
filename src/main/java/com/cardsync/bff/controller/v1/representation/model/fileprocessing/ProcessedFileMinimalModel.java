package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

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
public class ProcessedFileMinimalModel extends RepresentationModel<@NonNull ProcessedFileMinimalModel> {

  private UUID id;

  private String file;
}
