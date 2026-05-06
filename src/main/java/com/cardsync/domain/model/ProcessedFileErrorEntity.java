package com.cardsync.domain.model;

import com.cardsync.domain.model.enums.ProcessedFileErrorTypeEnum;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_processed_file_error")
public class ProcessedFileErrorEntity extends AuditableEntityBase {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "processed_file_id", nullable = false)
  private ProcessedFileEntity processedFile;

  @Column(name = "line_number")
  private Integer lineNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "error_type", nullable = false, length = 30)
  private ProcessedFileErrorTypeEnum errorType;

  @Column(name = "error_code", nullable = false, length = 80)
  private String errorCode;

  @Column(name = "message", nullable = false, length = 500)
  private String message;

  @Lob
  @Column(name = "raw_line", columnDefinition = "TEXT")
  private String rawLine;

  public static ProcessedFileErrorEntity of(Integer lineNumber, ProcessedFileErrorTypeEnum type, String code, String message, String rawLine) {
    ProcessedFileErrorEntity error = new ProcessedFileErrorEntity();
    error.setLineNumber(lineNumber);
    error.setErrorType(type);
    error.setErrorCode(code);
    error.setMessage(message);
    error.setRawLine(rawLine);
    return error;
  }
}
