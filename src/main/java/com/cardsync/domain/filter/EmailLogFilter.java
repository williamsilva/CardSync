package com.cardsync.domain.filter;

import com.nimbussystems.commons.legacy.model.enums.EmailLogEventTypeEnum;
import com.nimbussystems.commons.legacy.model.enums.EmailLogStatusEnum;
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