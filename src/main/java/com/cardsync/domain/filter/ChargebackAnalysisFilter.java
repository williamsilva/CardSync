package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.ChargebackAnalysisStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ChargebackAnalysisFilter(
  String global,
  List<ChargebackAnalysisStatus> statuses,
  LocalDate saleDateStart,
  LocalDate saleDateEnd,
  LocalDate requestDateStart,
  LocalDate requestDateEnd,
  LocalDate deadlineStart,
  LocalDate deadlineEnd,
  LocalDate debitDateStart,
  LocalDate debitDateEnd,
  LocalDate settlementDateStart,
  LocalDate settlementDateEnd,
  LocalDate eventDateStart,
  LocalDate eventDateEnd,
  BigDecimal valueStart,
  BigDecimal valueEnd,
  String nsu,
  String authorization,
  String tid,
  String processNumber,
  String debitOrderNumber,
  String reasonCode,
  String reason,
  String pvNumber,
  String rvNumber
) {}
