package com.cardsync.infrastructure.audit;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cs_audit_event")
public class AuditEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 80)
  private String eventType;

  @Column(length = 120)
  private String principal;

  @Column(length = 64)
  private String ip;

  @Column(length = 255)
  private String userAgent;

  @Column(columnDefinition = "BINARY(16)")
  private UUID correlationId;

  @Column(columnDefinition = "json")
  private String payloadJson;

  @Column(nullable = false)
  private OffsetDateTime createdAt;
}
