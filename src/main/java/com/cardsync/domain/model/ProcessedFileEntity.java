package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.FileGroupEnum;
import com.cardsync.domain.model.enums.FileStatusEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(
  name = "cs_processed_file",
  uniqueConstraints = @UniqueConstraint(name = "uk_cs_processed_file_file_origin", columnNames = {"file_name", "origin_file_id"})
)
public class ProcessedFileEntity extends AuditableEntityBase {
  @Column(name = "file_name", nullable = false, length = 255)
  private String file;

  @Enumerated(EnumType.STRING)
  @Column(name = "file_group", nullable = false, length = 20)
  private FileGroupEnum group;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private FileStatusEnum status = FileStatusEnum.RECEIVED;

  @Column(name = "date_file")
  private LocalDate dateFile;
  @Column(name = "date_import", nullable = false)
  private OffsetDateTime dateImport;
  @Column(name = "date_processing", nullable = false)
  private OffsetDateTime dateProcessing;
  @Column(name = "started_at")
  private OffsetDateTime startedAt;
  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

  @Column(name = "type_file", length = 120)
  private String typeFile;
  @Column(name = "commercial_name", length = 150)
  private String commercialName;
  @Column(name = "version", length = 80)
  private String version;
  @Column(name = "pv_group_number")
  private Integer pvGroupNumber;

  @Column(name = "total_lines")
  private Integer totalLines = 0;
  @Column(name = "processed_lines")
  private Integer processedLines = 0;
  @Column(name = "ignored_lines")
  private Integer ignoredLines = 0;
  @Column(name = "warning_lines")
  private Integer warningLines = 0;
  @Column(name = "error_lines")
  private Integer errorLines = 0;
  @Column(name = "pending_contract_lines")
  private Integer pendingContractLines = 0;
  @Column(name = "pending_business_context_lines")
  private Integer pendingBusinessContextLines = 0;

  @Column(name = "error_message", length = 500)
  private String errorMessage;
  @Column(name = "status_message", length = 500)
  private String statusMessage;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "origin_file_id", nullable = false)
  private OriginFileEntity originFile;

  @OneToMany(mappedBy = "processedFile", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ProcessedFileErrorEntity> errors = new ArrayList<>();

  public void addError(ProcessedFileErrorEntity error) {
    errors.add(error);
    error.setProcessedFile(this);
  }

  public void markProcessing() {
    this.status = FileStatusEnum.PROCESSING;
    this.startedAt = OffsetDateTime.now();
    this.dateProcessing = this.startedAt;
  }

  public void markFinished(FileStatusEnum status, String message) {
    this.status = status;
    this.statusMessage = message;
    this.finishedAt = OffsetDateTime.now();
  }
}
