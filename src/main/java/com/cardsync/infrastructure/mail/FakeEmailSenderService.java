package com.cardsync.infrastructure.mail;

import com.cardsync.domain.service.EmailSenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class FakeEmailSenderService implements EmailSenderService {

  @Autowired
  private EmailTemplateProcessor emailTemplateProcessor;

  @Override
  public void sendFreemarker(Message message) {
    String body = emailTemplateProcessor.processTemplate(message);

    log.info("[SEND FAKE E-MAIL Freemarker] ReplyTo: {}", message.getReplyTo());
    log.info("[SEND FAKE E-MAIL Freemarker] To: {}\n{}", message.getRecipients(), body);
  }

  @Override
  public void sendThymeleaf(Message message) {
    String body = emailTemplateProcessor.processTemplateThymeleaf(message);

    log.info("[SEND FAKE E-MAIL Thymeleaf] ReplyTo: {}", message.getReplyTo());
    log.info("[SEND FAKE E-MAIL Thymeleaf] To: {}\n{}", message.getRecipients(), body);
  }

}
