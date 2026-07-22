package com.cardsync.bff.controller.v1.representation.input;

import com.cardsync.core.backup.BackupTarget;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BackupExecuteInput(
  @NotNull
  @NotEmpty
  List<BackupTarget> targets
) {}
