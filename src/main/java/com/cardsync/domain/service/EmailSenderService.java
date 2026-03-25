package com.cardsync.domain.service;

import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;
import org.springframework.core.io.Resource;

public interface EmailSenderService {

  void sendFreemarker(Message message);

  void sendThymeleaf(Message message);

  @Getter
  @Builder
  @ToString
  class Message {

    private String replyTo;

    @Singular("to")
    private Set<String> recipients;

    @NonNull
    private String subject;

    @NonNull
    private String template;

    private Locale locale;

    private UUID requestedByUserId;

    @NonNull
    private EmailLogEventTypeEnum eventType;

    @Singular("data")
    private Map<String, Object> data;

    @Singular("inline")
    private Set<InlineResource> inlines;
  }

  @Getter
  @Builder
  @ToString
  class InlineResource {

    @NonNull
    private String contentId;

    @NonNull
    private Resource resource;

    @NonNull
    private String contentType;
  }
}