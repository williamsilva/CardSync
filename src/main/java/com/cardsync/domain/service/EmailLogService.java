package com.cardsync.domain.service;

import com.cardsync.domain.filter.EmailLogFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.EmailLogEntity;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import com.cardsync.domain.model.enums.EmailLogStatusEnum;
import com.cardsync.domain.repository.EmailLogRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.cardsync.infrastructure.repository.spec.EmailLogSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailLogService {

  private final EmailLogSpecs emailLogSpecs;
  private final EmailLogRepository repository;

  @Transactional(readOnly = true)
  public Page<EmailLogEntity> list(Pageable pageable, ListQueryDto<EmailLogFilter> query) {
    Specification<EmailLogEntity> spec = emailLogSpecs.fromQuery(query);
    return repository.findAll(spec, pageable);
  }

  @Transactional
  public void logSent(EmailLogEventTypeEnum eventType, String recipient, String subject,
    String template, UserEntity requestedBy) {
    EmailLogEntity e = new EmailLogEntity();
    e.setEventType(eventType);
    e.setRecipient(recipient);
    e.setSubject(subject);
    e.setTemplate(template);
    e.setStatus(EmailLogStatusEnum.SENT);
    e.setRequestedBy(requestedBy);
    e.setSentAt(nowUtc());

    repository.save(e);
  }

  @Transactional
  public void logError( EmailLogEventTypeEnum eventType, String recipient, String subject,
    String template, UserEntity requestedBy, Exception ex) {
    EmailLogEntity e = new EmailLogEntity();
    e.setEventType(eventType);
    e.setRecipient(recipient);
    e.setSubject(subject);
    e.setTemplate(template);
    e.setStatus(EmailLogStatusEnum.FAILED);
    e.setRequestedBy(requestedBy);
    e.setErrorMessage(truncate(ex.getMessage(), 1000));
    e.setSentAt(nowUtc());

    repository.save(e);
  }

  private OffsetDateTime nowUtc() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private String truncate(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }
}