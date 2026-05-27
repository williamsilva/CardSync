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

  private String trackingKey;

  private ChargebackAnalysisStatus currentStatus;
  private LocalDate firstEventDate;
  private LocalDate lastEventDate;

  private String company;
  private String establishment;
  private String acquirer;
  private String flag;

  private Integer pvNumber;
  private Integer originalPvNumber;
  private Integer rvNumber;
  private Integer originalRvNumber;

  private Long nsu;
  private String authorization;
  private String tid;
  private String orderNumber;

  private String processNumber;
  private String debitOrderNumber;
  private String retentionProcessNumber;

  private BigDecimal saleValue;
  private BigDecimal disputedValue;
  private BigDecimal pendingValue;
  private BigDecimal settledValue;
  private BigDecimal compensatedValue;

  private String reasonCode;
  private String reasonDescription;
  private String requestedDocuments;

  private String compensationCode;
  private String compensationDescription;

  @Builder.Default
  private List<ChargebackTimelineEventModel> timeline = new ArrayList<>();
}
