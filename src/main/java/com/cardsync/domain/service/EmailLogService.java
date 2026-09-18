package com.cardsync.domain.service;

import com.cardsync.domain.model.EmailLogEntity;
import com.nimbussystems.commons.legacy.model.enums.EmailLogEventTypeEnum;
import com.nimbussystems.commons.legacy.model.enums.EmailLogStatusEnum;
import com.nimbussystems.commons.notification.mail.EmailDeliveryLogger;
import com.nimbussystems.commons.notification.mail.EmailSenderService;
import com.cardsync.domain.repository.EmailLogRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Implementa EmailDeliveryLogger (contrato mínimo exigido por EmailSenderServiceRouter/Brevo-
 *  Smtp-FakeEmailSenderService da lib compartilhada) em cima do cs_email_log - grava envio/erro. A
 *  listagem/busca (antes exposta em EmailLogController/EmailLogModel/EmailLogSpecs, BFF) foi
 *  removida junto com a tela local `/audit` (Fase 5 da consolidação de Segurança) - a auditoria de
 *  e-mail agora é federada e centralizada no NimbusCoreWeb (ver InternalEmailLogController). */
@Service
@RequiredArgsConstructor
public class EmailLogService implements EmailDeliveryLogger {

  private final EmailLogRepository repository;

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