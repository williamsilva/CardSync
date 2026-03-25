package com.cardsync.infrastructure.mail;

import com.cardsync.core.config.EmailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.MimeMessageHelper;

public class SandboxEmailSenderService extends SmtpEmailSenderService {

  @Autowired
  private EmailProperties emailProperties;

  @Override
  protected MimeMessage createMimeMessageFreemarker(Message message) throws MessagingException {
    MimeMessage mimeMessage = super.createMimeMessageFreemarker(message);

    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
    helper.setTo(emailProperties.getSandbox().getTo());

    return mimeMessage;
  }

}
