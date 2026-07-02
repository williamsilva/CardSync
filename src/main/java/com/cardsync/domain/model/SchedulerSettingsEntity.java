package com.cardsync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "cs_scheduler_settings")
public class SchedulerSettingsEntity extends AuditableEntityBase {

  @Column(name = "enabled", nullable = false)
  private boolean enabled = false;

  @Column(name = "complete_pipeline_enabled", nullable = false)
  private boolean completePipelineEnabled = true;

  @Column(name = "complete_pipeline_cron", nullable = false, length = 100)
  private String completePipelineCron = "0 0/30 * * * *";

  @Column(name = "complete_pipeline_stop_on_step_error", nullable = false)
  private boolean completePipelineStopOnStepError = true;

  @Column(name = "log_idle_cycles", nullable = false)
  private boolean logIdleCycles = false;
}
