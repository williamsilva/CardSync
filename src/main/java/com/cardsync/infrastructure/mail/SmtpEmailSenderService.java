package com.cardsync.infrastructure.mail;

import com.cardsync.core.config.EmailSettingsService;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.service.EmailLogService;
import com.cardsync.domain.service.EmailSenderService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Properties;
import java.util.UUID;

public class SmtpEmailSenderService implements EmailSenderService {

  @Autowired
  private EmailSettingsService emailSettingsService;

  @Autowired
  private EmailTemplateProcessor emailTemplateProcessor;

  @Autowired
  private EmailLogService emailLogService;

  @Override
  public void sendFreemarker(Message message) {
    UUID requestedBy = resolveRequestedBy(message);
    JavaMailSender mailSender = buildMailSender();
    try {
      MimeMessage mimeMessage = createMimeMessageFreemarker(message, mailSender);
      mailSender.send(mimeMessage);
      emailLogService.logSent(message.getEventType(), firstRecipient(message), message.getSubject(), message.getTemplate(), requestedBy);
    } catch (MailAuthenticationException e) {
      emailLogService.logError(message.getEventType(), firstRecipient(message), message.getSubject(), message.getTemplate(), requestedBy, e);
      throw BusinessException.notFound(ErrorCode.EMAIL_AUTHENTICATION_FAILED, "Could not send email, authentication failed", e);
    } catch (Exception e) {
      emailLogService.logError(message.getEventType(), firstRecipient(message), message.getSubject(), message.getTemplate(), requestedBy, e);
      throw BusinessException.notFound(ErrorCode.EMAIL_NOT_SEND, "Could not send email", e);
    }
  }

  @Override
  public void sendThymeleaf(Message message) {
    UUID requestedBy = resolveRequestedBy(message);
    JavaMailSender mailSender = buildMailSender();
    try {
      MimeMessage mimeMessage = createMimeMessageThymeleaf(message, mailSender);
      mailSender.send(mimeMessage);
      emailLogService.logSent(message.getEventType(), firstRecipient(message), message.getSubject(), message.getTemplate(), requestedBy);
    } catch (MailAuthenticationException e) {
      emailLogService.logError(message.getEventType(), firstRecipient(message), message.getSubject(), message.getTemplate(), requestedBy, e);
      throw BusinessException.notFound(ErrorCode.EMAIL_AUTHENTICATION_FAILED, "Could not send email, authentication failed", e);
    } catch (Exception e) {
      emailLogService.logError(message.getEventType(), firstRecipient(message), message.getSubject(), message.getTemplate(), requestedBy, e);
      throw BusinessException.notFound(ErrorCode.EMAIL_NOT_SEND, "Could not send email", e);
    }
  }

  private JavaMailSender buildMailSender() {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(emailSettingsService.getSmtpHost());
    Integer port = emailSettingsService.getSmtpPort();
    sender.setPort(port != null ? port : 587);
    sender.setUsername(emailSettingsService.getSmtpUsername());
    sender.setPassword(emailSettingsService.getSmtpPassword());

    Properties props = sender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", String.valueOf(Boolean.TRUE.equals(emailSettingsService.getSmtpAuth())));
    props.put("mail.smtp.starttls.enable", String.valueOf(Boolean.TRUE.equals(emailSettingsService.getSmtpStarttls())));
    props.put("mail.smtp.ssl.enable", String.valueOf(Boolean.TRUE.equals(emailSettingsService.getSmtpSsl())));

    return sender;
  }

  protected MimeMessage createMimeMessageThymeleaf(Message message, JavaMailSender mailSender) throws MessagingException {
    String body = emailTemplateProcessor.processTemplateThymeleaf(message);
    return buildMimeMessage(message, body, mailSender);
  }

  protected MimeMessage createMimeMessageFreemarker(Message message, JavaMailSender mailSender) throws MessagingException {
    String body = emailTemplateProcessor.processTemplate(message);
    return buildMimeMessage(message, body, mailSender);
  }

  protected MimeMessage buildMimeMessage(Message message, String body, JavaMailSender mailSender) throws MessagingException {
    MimeMessage mimeMessage = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

    helper.setText(body, true);
    helper.setSubject(message.getSubject());
    helper.setFrom(emailSettingsService.getFromEmail());
    helper.setTo(message.getRecipients().toArray(new String[0]));

    if (message.getReplyTo() != null && !message.getReplyTo().isBlank()) {
      helper.setReplyTo(message.getReplyTo());
    }

    addInlineResources(helper, message);
    return mimeMessage;
  }

  protected void addInlineResources(MimeMessageHelper helper, Message message) throws MessagingException {
    if (message.getInlines() == null || message.getInlines().isEmpty()) return;
    for (EmailSenderService.InlineResource inline : message.getInlines()) {
      helper.addInline(inline.getContentId(), inline.getResource(), inline.getContentType());
    }
  }

  protected UUID resolveRequestedBy(Message message) {
    return message.getRequestedByUserId();
  }

  protected String firstRecipient(Message message) {
    return message.getRecipients().stream().findFirst().orElse("unknown");
  }
}
