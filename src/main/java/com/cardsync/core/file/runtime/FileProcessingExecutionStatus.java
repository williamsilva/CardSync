package com.cardsync.core.file.runtime;

import com.cardsync.domain.model.enums.FileProcessingTriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FileProcessingExecutionStatus {

  private FileProcessingSystemType system;
  private boolean running;
  private OffsetDateTime lastStartedAt;
  private OffsetDateTime lastFinishedAt;
  private Boolean lastSuccess;
  private FileProcessingTriggerType lastTrigger;
  private String lastMessage;
}
