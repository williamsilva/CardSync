package com.cardsync.infrastructure.mail;

import com.cardsync.core.config.EmailProperties;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.repository.UserRepository;
import com.cardsync.domain.service.EmailLogService;
import com.cardsync.domain.service.EmailSenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
public class BrevoEmailSenderService implements EmailSenderService {

  private final RestClient restClient;
  private final UserRepository userRepository;
  private final EmailProperties emailProperties;
  private final EmailLogService emailLogService;
  private final EmailTemplateProcessor emailTemplateProcessor;

  public BrevoEmailSenderService(
    RestClient restClient,
    UserRepository userRepository,
    EmailProperties emailProperties,
    EmailLogService emailLogService,
    EmailTemplateProcessor emailTemplateProcessor
  ) {
    this.restClient = restClient;
    this.userRepository = userRepository;
    this.emailProperties = emailProperties;
    this.emailLogService = emailLogService;
    this.emailTemplateProcessor = emailTemplateProcessor;
  }

  @Override
  public void sendFreemarker(Message message) {
    send(message, emailTemplateProcessor.processTemplate(message));
  }

  @Override
  public void sendThymeleaf(Message message) {
    send(message, emailTemplateProcessor.processTemplateThymeleaf(message));
  }

  private void send(Message message, String htmlBody) {
    UserEntity requestedBy = resolveRequestedBy(message);

    try {
      BrevoSendEmailRequest payload = BrevoSendEmailRequest.builder()
        .sender(new BrevoSendEmailRequest.Sender(
          emailProperties.getFromName(),
          emailProperties.getFromEmail()))
        .to(message.getRecipients().stream()
          .map(email -> new BrevoSendEmailRequest.Recipient(email, null))
          .toList())
        .replyTo(message.getReplyTo() != null && !message.getReplyTo().isBlank()
          ? new BrevoSendEmailRequest.ReplyTo(message.getReplyTo(), null)
          : null)
        .subject(message.getSubject())
        .htmlContent(htmlBody)
        .build();

      restClient.post()
        .uri("/smtp/email")
        .header("api-key", emailProperties.getBrevo().getApiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .toBodilessEntity();

      emailLogService.logSent(
        message.getEventType(),
        firstRecipient(message),
        message.getSubject(),
        message.getTemplate(),
        requestedBy
      );
    } catch (Exception e) {
      emailLogService.logError(
        message.getEventType(),
        firstRecipient(message),
        message.getSubject(),
        message.getTemplate(),
        requestedBy,
        e
      );

      throw BusinessException.notFound(
        ErrorCode.EMAIL_NOT_SEND,
        "Could not send email via Brevo",
        e
      );
    }
  }

  private UserEntity resolveRequestedBy(Message message) {
    if (message.getRequestedByUserId() == null) {
      return null;
    }
    return userRepository.findById(message.getRequestedByUserId()).orElse(null);
  }

  private String firstRecipient(Message message) {
    return message.getRecipients().stream().findFirst().orElse("unknown");
  }
}