package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.EmailLogEventTypeEnum;
import com.cardsync.domain.model.enums.EmailLogStatusEnum;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "cs_email_log")
public class EmailLogEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 320)
  private String recipient;

  @Column(nullable = false, length = 300)
  private String subject;

  @Column(nullable = false, length = 200)
  private String template;

  @Column(nullable = false)
  private Integer status;

  @Column(nullable = false)
  private Integer eventType;

  @Column(length = 1000)
  private String errorMessage;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requested_by_id")
  private UserEntity requestedBy;

  @LastModifiedDate
  private OffsetDateTime updatedAt;

  @Column(nullable = false)
  private OffsetDateTime sentAt;

  @CreatedBy
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private UserEntity createdBy;

  @LastModifiedBy
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "last_modified_by")
  private UserEntity lastModifiedBy;

  @CreatedDate
  @Column(nullable = false)
  private OffsetDateTime createdAt;

  public EmailLogStatusEnum getStatus() {
    return EmailLogStatusEnum.fromCode(this.status);
  }

  public void setStatus(EmailLogStatusEnum status) {
    this.status = EmailLogStatusEnum.toCode(status);
  }

  public EmailLogEventTypeEnum getEventType() {
    return EmailLogEventTypeEnum.fromCode(this.eventType);
  }

  public void setEventType(EmailLogEventTypeEnum eventType) {
    this.eventType = EmailLogEventTypeEnum.toCode(eventType);
  }
}
