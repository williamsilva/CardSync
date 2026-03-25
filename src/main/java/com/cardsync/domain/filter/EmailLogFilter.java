package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import com.cardsync.domain.model.enums.EmailLogStatusEnum;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class EmailLogFilter {

  private String subject;
  private String template;
  private String recipient;

  private String sentAtTo;
  private String sentAtFrom;

  private List<EmailLogStatusEnum> status;
  private List<EmailLogEventTypeEnum> eventType;
}