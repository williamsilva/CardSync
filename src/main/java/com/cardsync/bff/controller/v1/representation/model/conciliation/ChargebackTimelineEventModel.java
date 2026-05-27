package com.cardsync.bff.controller.v1.representation.model.conciliation;

import com.cardsync.domain.model.enums.ChargebackAnalysisStatus;
import com.cardsync.domain.model.enums.ChargebackEventSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChargebackTimelineEventModel {

  private UUID id;
  private ChargebackAnalysisStatus status;
  private ChargebackEventSourceType sourceType;
  private LocalDate eventDate;

  private String title;
  private String description;

  private BigDecimal amount;
  private BigDecimal pendingValue;
  private BigDecimal settledValue;
  private BigDecimal compensatedValue;

  private String processNumber;
  private String debitOrderNumber;
  private String processedFile;
}
