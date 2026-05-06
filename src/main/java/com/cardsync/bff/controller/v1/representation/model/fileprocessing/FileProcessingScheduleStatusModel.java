package com.cardsync.bff.controller.v1.representation.model.fileprocessing;

import com.cardsync.core.file.runtime.FileProcessingExecutionStatus;

import java.time.OffsetDateTime;

public record FileProcessingScheduleStatusModel(
  String system,
  boolean running,
  OffsetDateTime lastStartedAt,
  OffsetDateTime lastFinishedAt,
  Boolean lastSuccess,
  String lastTrigger,
  String lastMessage
) {
  public static FileProcessingScheduleStatusModel from(FileProcessingExecutionStatus status) {
    return new FileProcessingScheduleStatusModel(
      status.system(),
      status.running(),
      status.lastStartedAt(),
      status.lastFinishedAt(),
      status.lastSuccess(),
      status.lastTrigger(),
      status.lastMessage()
    );
  }
}
