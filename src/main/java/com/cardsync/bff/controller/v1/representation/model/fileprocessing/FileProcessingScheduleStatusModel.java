package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import com.cardsync.core.file.runtime.FileProcessingExecutionStatus;
import com.cardsync.core.file.runtime.FileProcessingSystemType;
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
public class FileProcessingScheduleStatusModel {

  private FileProcessingSystemType system;
  private boolean running;
  private OffsetDateTime lastStartedAt;
  private OffsetDateTime lastFinishedAt;
  private Boolean lastSuccess;
  private FileProcessingTriggerType lastTrigger;
  private String lastMessage;

  public static FileProcessingScheduleStatusModel from(FileProcessingExecutionStatus status) {
    return FileProcessingScheduleStatusModel.builder()
      .system(status.getSystem())
      .running(status.isRunning())
      .lastStartedAt(status.getLastStartedAt())
      .lastFinishedAt(status.getLastFinishedAt())
      .lastSuccess(status.getLastSuccess())
      .lastTrigger(status.getLastTrigger())
      .lastMessage(status.getLastMessage())
      .build();
  }
}
