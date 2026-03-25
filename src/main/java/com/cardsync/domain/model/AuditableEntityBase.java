package com.cardsync.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private OffsetDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  @CreatedBy
  @JsonIgnore
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_id")
  private UserEntity createdBy;

  @JsonIgnore
  @LastModifiedBy
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "updated_by_id")
  private UserEntity updatedBy;
}
