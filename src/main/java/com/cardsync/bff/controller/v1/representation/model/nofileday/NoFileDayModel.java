package com.cardsync.bff.controller.v1.representation.model.nofileday;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileMinimalModel;
import com.cardsync.domain.model.enums.AcquirerFileTypeEnum;
import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.NoFileDayTypeEnum;
import com.cardsync.domain.model.enums.StatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class NoFileDayModel extends RepresentationModel<NoFileDayModel> {

  private UUID id;
  private String description;

  private StatusEnum status;
  private FileGroupEnum fileGroup;
  private NoFileDayTypeEnum dayType;

  private AcquirerMinimalModel acquirer;
  private AcquirerFileTypeEnum acquirerFileType;
  private BankingDomicileMinimalModel bankingDomicile;

  private LocalDate noFileDate;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
  private OffsetDateTime statusDate;

}