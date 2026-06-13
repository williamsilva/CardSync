package com.cardsync.bff.controller.v1.representation.model.conciliation;

import com.cardsync.domain.model.enums.ChargebackAnalysisStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class ChargebackLifecycleModel {

  private Long nsu;

  private Integer pvNumber;
  private Integer rvNumber;
  private Integer originalPvNumber;
  private Integer originalRvNumber;

  private String tid;
  private String flag;
  private String company;
  private String acquirer;
  private String reasonCode;
  private String trackingKey;
  private String orderNumber;
  private String establishment;
  private String processNumber;
  private String authorization;
  private String debitOrderNumber;
  private String compensationCode;
  private String reasonDescription;
  private String requestedDocuments;
  private String retentionProcessNumber;
  private String compensationDescription;

  private BigDecimal saleValue;
  private BigDecimal pendingValue;
  private BigDecimal settledValue;
  private BigDecimal disputedValue;
  private BigDecimal compensatedValue;

  private LocalDate lastEventDate;
  private LocalDate firstEventDate;

  private ChargebackAnalysisStatus currentStatus;

  @Builder.Default
  private List<ChargebackTimelineEventModel> timeline = new ArrayList<>();
}
