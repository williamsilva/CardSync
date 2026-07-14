package com.cardsync.domain.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * createdBy/updatedBy sao o UUID do usuario no NimbusAuth (dono de UserEntity apos o split
 * Cardsync/NimbusAuth) - sem FK local, ja que cs_users nao existe mais neste schema.
 * Para exibir nome/username, ver UserDirectoryService.
 */
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
  @Column(name = "created_by_id", updatable = false)
  private UUID createdBy;

  @LastModifiedBy
  @Column(name = "updated_by_id")
  private UUID updatedBy;
}
