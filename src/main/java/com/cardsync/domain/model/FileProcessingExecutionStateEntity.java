package com.cardsync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "cs_file_processing_execution_state")
public class FileProcessingExecutionStateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "system_code", nullable = false, unique = true, length = 20)
  private String system;

  @Column(name = "last_started_at")
  private OffsetDateTime lastStartedAt;

  @Column(name = "last_finished_at")
  private OffsetDateTime lastFinishedAt;

  @Column(name = "last_success")
  private Boolean lastSuccess;

  @Column(name = "last_trigger", length = 80)
  private String lastTrigger;

  @Column(name = "last_message", columnDefinition = "TEXT")
  private String lastMessage;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;
}
