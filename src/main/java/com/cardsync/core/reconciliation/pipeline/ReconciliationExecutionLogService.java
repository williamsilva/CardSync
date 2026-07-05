package com.cardsync.core.reconciliation.pipeline;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ReconciliationExecutionLogResponse;
import com.cardsync.domain.model.ReconciliationExecutionLogEntity;
import com.cardsync.domain.model.enums.ReconciliationPipelineStepStatusEnum;
import com.cardsync.infrastructure.repository.ReconciliationExecutionLogRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationExecutionLogService {

    private final ObjectMapper objectMapper;
    private final ReconciliationExecutionLogRepository repository;

    @Transactional
    public void save(FinancialReconciliationPipelineResult result) {
        try {
            String stepsJson = objectMapper.writeValueAsString(result.getSteps());

            int totalAnalyzed = 0;
            int totalReconciled = 0;
            int totalPending = 0;
            boolean anyFailed = false;
            boolean allCompleted = true;

            for (FinancialReconciliationStepResult step : result.getSteps()) {
                totalAnalyzed += step.getAnalyzed();
                totalReconciled += step.getReconciled();
                totalPending += step.getPending();
                if (step.getStatus() == ReconciliationPipelineStepStatusEnum.FAILED) anyFailed = true;
                if (step.getStatus() != ReconciliationPipelineStepStatusEnum.COMPLETED) allCompleted = false;
            }

            String overallStatus = anyFailed ? "FAILED" : allCompleted ? "SUCCESS" : "PARTIAL";

            ReconciliationExecutionLogEntity entity = new ReconciliationExecutionLogEntity();
            entity.setTriggerType(result.getTrigger().name());
            entity.setStartedAt(result.getStartedAt());
            entity.setFinishedAt(result.getFinishedAt());
            entity.setOverallStatus(overallStatus);
            entity.setTotalAnalyzed(totalAnalyzed);
            entity.setTotalReconciled(totalReconciled);
            entity.setTotalPending(totalPending);
            entity.setStepsJson(stepsJson);

            repository.save(entity);
        } catch (JacksonException e) {
            log.error("Erro ao serializar steps do log de execução da esteira", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ReconciliationExecutionLogResponse> findRecent(int limit) {
        return repository.findTop50ByOrderByStartedAtDesc()
            .stream()
            .limit(limit)
            .map(this::toResponse)
            .toList();
    }

    private ReconciliationExecutionLogResponse toResponse(ReconciliationExecutionLogEntity entity) {
        List<FinancialReconciliationStepResult> steps = Collections.emptyList();
        if (entity.getStepsJson() != null) {
            try {
                steps = objectMapper.readValue(
                    entity.getStepsJson(),
                    new TypeReference<List<FinancialReconciliationStepResult>>() {}
                );
            } catch (JacksonException e) {
                log.warn("Falha ao desserializar steps do log id={}", entity.getId(), e);
            }
        }
        return new ReconciliationExecutionLogResponse(
            entity.getId().toString(),
            entity.getTriggerType(),
            entity.getStartedAt(),
            entity.getFinishedAt(),
            entity.getOverallStatus(),
            entity.getTotalAnalyzed(),
            entity.getTotalReconciled(),
            entity.getTotalPending(),
            steps
        );
    }
}
