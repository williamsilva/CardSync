package com.cardsync.bff.controller.v1.representation.model.holiday;

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
public class HolidayModel extends RepresentationModel<HolidayModel>{

  private UUID id;
  private String name;
  private Boolean recurring;
  private StatusEnum status;

  private LocalDate holidayDate;
  private OffsetDateTime statusDate;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
}
