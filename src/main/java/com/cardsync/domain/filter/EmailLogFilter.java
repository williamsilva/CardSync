package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import com.cardsync.domain.model.enums.EmailLogStatusEnum;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

public record EmailLogFilter (
  String subject,
  String template,
  String recipient,

  String sentAtTo,
  String sentAtFrom,

  List<String> createdBy,
   List<EmailLogStatusEnum> status,
  List<EmailLogEventTypeEnum> eventType
) {
}