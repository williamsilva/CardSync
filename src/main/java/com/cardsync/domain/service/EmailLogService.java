package com.cardsync.domain.service;

import com.cardsync.domain.filter.EmailLogFilter;
import com.nimbussystems.commons.legacy.filter.query.ListQueryDto;
import com.cardsync.domain.model.EmailLogEntity;
import com.nimbussystems.commons.legacy.model.enums.EmailLogEventTypeEnum;
import com.nimbussystems.commons.legacy.model.enums.EmailLogStatusEnum;
import com.nimbussystems.commons.notification.mail.EmailDeliveryLogger;
import com.nimbussystems.commons.notification.mail.EmailSenderService;
import com.cardsync.domain.repository.EmailLogRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.cardsync.infrastructure.repository.spec.EmailLogSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implementa EmailDeliveryLogger (contrato mínimo exigido por EmailSenderServiceRouter/Brevo-
 *  Smtp-FakeEmailSenderService da lib compartilhada) em cima do EmailLogService/cs_email_log já
 *  existentes - só um adapter de assinatura, a tela de auditoria e o resto deste service não
 *  mudam em nada (ver plano da Fase 4: log NÃO foi migrado pra tabela/entidade compartilhada). */
@Service
@RequiredArgsConstructor
public class EmailLogService implements EmailDeliveryLogger {

  private final EmailLogSpecs emailLogSpecs;
  private final EmailLogRepository repository;

  @Transactional(readOnly = true)
  public Page<EmailLogEntity> list(Pageable pageable, ListQueryDto<EmailLogFilter> query) {
    Specification<EmailLogEntity> spec = emailLogSpecs.fromQuery(query);
    return repository.findAll(spec, pageable);
  }

  @Transactional
  public void logSent(EmailLogEventTypeEnum eventType, String recipient, String subject,
    String template, UUID requestedBy) {
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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void logError( EmailLogEventTypeEnum eventType, String recipient, String subject,
    String template, UUID requestedBy, Exception ex) {
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

  @Override
  public void logSent(EmailSenderService.Message message, String body) {
    logSent(EmailLogEventTypeEnum.valueOf(message.getEventType()),
        String.join(", ", message.getRecipients()), message.getSubject(), message.getTemplate(),
        toUuidOrNull(message.getRequestedById()));
  }

  @Override
  public void logError(EmailSenderService.Message message, String body, Exception ex) {
    logError(EmailLogEventTypeEnum.valueOf(message.getEventType()),
        String.join(", ", message.getRecipients()), message.getSubject(), message.getTemplate(),
        toUuidOrNull(message.getRequestedById()), ex);
  }

  private UUID toUuidOrNull(String id) {
    return (id == null || id.isBlank()) ? null : UUID.fromString(id);
  }
}