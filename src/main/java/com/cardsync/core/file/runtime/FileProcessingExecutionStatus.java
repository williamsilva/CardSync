package com.cardsync.core.file.runtime;

import java.time.OffsetDateTime;

public record FileProcessingExecutionStatus(
  String system,
  boolean running,
  OffsetDateTime lastStartedAt,
  OffsetDateTime lastFinishedAt,
  Boolean lastSuccess,
  String lastTrigger,
  String lastMessage
) {
}
