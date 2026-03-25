package com.cardsync.infrastructure.mail;

import com.cardsync.core.config.EmailProperties;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.model.UserEntity;
import com.cardsync.domain.repository.UserRepository;
import com.cardsync.domain.service.EmailLogService;
import com.cardsync.domain.service.EmailSenderService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class SmtpEmailSenderService implements EmailSenderService {

  @Autowired
  private JavaMailSender mailSender;

  @Autowired
  private EmailProperties emailProperties;

  @Autowired
  private EmailTemplateProcessor emailTemplateProcessor;

  @Autowired
  private EmailLogService emailLogService;

  @Autowired
  private UserRepository userRepository;

  @Override
  public void sendFreemarker(Message message) {
    UserEntity requestedBy = resolveRequestedBy(message);

    try {
      MimeMessage mimeMessage = createMimeMessageFreemarker(message);
      mailSender.send(mimeMessage);

      emailLogService.logSent(
        message.getEventType(),
        firstRecipient(message),
        message.getSubject(),
        message.getTemplate(),
        requestedBy
      );
    } catch (MailAuthenticationException e) {
      emailLogService.logError(
        message.getEventType(),
        firstRecipient(message),
        message.getSubject(),
        message.getTemplate(),
        requestedBy,
        e
      );

      throw BusinessException.notFound(
        ErrorCode.EMAIL_AUTHENTICATION_FAILED,
        "Could not send email, authentication failed",
        e
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
        "Could not send email",
        e
      );
    }
  }

  @Override
  public void sendThymeleaf(Message message) {
    UserEntity requestedBy = resolveRequestedBy(message);

    try {
      MimeMessage mimeMessage = createMimeMessageThymeleaf(message);
      mailSender.send(mimeMessage);

      emailLogService.logSent(
        message.getEventType(),
        firstRecipient(message),
        message.getSubject(),
        message.getTemplate(),
        requestedBy
      );
    } catch (MailAuthenticationException e) {
      emailLogService.logError(
        message.getEventType(),
        firstRecipient(message),
        message.getSubject(),
        message.getTemplate(),
        requestedBy,
        e
      );

      throw BusinessException.notFound(
        ErrorCode.EMAIL_AUTHENTICATION_FAILED,
        "Could not send email, authentication failed",
        e
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
        "Could not send email",
        e
      );
    }
  }

  protected MimeMessage createMimeMessageThymeleaf(Message message) throws MessagingException {
    String body = emailTemplateProcessor.processTemplateThymeleaf(message);
    return buildMimeMessage(message, body);
  }

  protected MimeMessage createMimeMessageFreemarker(Message message) throws MessagingException {
    String body = emailTemplateProcessor.processTemplate(message);
    return buildMimeMessage(message, body);
  }

  protected MimeMessage buildMimeMessage(Message message, String body) throws MessagingException {
    MimeMessage mimeMessage = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

    helper.setText(body, true);
    helper.setSubject(message.getSubject());
    helper.setFrom(emailProperties.getFrom());
    helper.setTo(message.getRecipients().toArray(new String[0]));

    if (message.getReplyTo() != null && !message.getReplyTo().isBlank()) {
      helper.setReplyTo(message.getReplyTo());
    }

    addInlineResources(helper, message);

    return mimeMessage;
  }

  protected void addInlineResources(MimeMessageHelper helper, Message message) throws MessagingException {
    if (message.getInlines() == null || message.getInlines().isEmpty()) {
      return;
    }

    for (EmailSenderService.InlineResource inline : message.getInlines()) {
      helper.addInline(
        inline.getContentId(),
        inline.getResource(),
        inline.getContentType()
      );
    }
  }

  protected UserEntity resolveRequestedBy(Message message) {
    if (message.getRequestedByUserId() == null) {
      return null;
    }

    return userRepository.findById(message.getRequestedByUserId()).orElse(null);
  }

  protected String firstRecipient(Message message) {
    return message.getRecipients().stream().findFirst().orElse("unknown");
  }
}