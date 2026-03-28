package com.cardsync.infrastructure.mail;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BrevoSendEmailRequest {

  private Sender sender;
  private String subject;
  private ReplyTo replyTo;
  private List<Recipient> to;
  private String htmlContent;

  @Getter
  @AllArgsConstructor
  public static class Sender {
    private String name;
    private String email;
  }

  @Getter
  @AllArgsConstructor
  public static class Recipient {
    private String email;
    private String name;
  }

  @Getter
  @AllArgsConstructor
  public static class ReplyTo {
    private String email;
    private String name;
  }
}