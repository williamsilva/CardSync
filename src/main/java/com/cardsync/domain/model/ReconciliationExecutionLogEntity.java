package com.cardsync.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "cs_reconciliation_execution_log")
public class ReconciliationExecutionLogEntity extends AuditableEntityBase {

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    private OffsetDateTime finishedAt;

    @Column(nullable = false)
    private String overallStatus;

    private Integer totalAnalyzed;
    private Integer totalReconciled;
    private Integer totalPending;

    @Column(columnDefinition = "TEXT")
    private String stepsJson;
}
