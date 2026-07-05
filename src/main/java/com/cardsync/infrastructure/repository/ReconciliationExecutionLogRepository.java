package com.cardsync.infrastructure.repository;

import com.cardsync.domain.model.ReconciliationExecutionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationExecutionLogRepository extends JpaRepository<ReconciliationExecutionLogEntity, UUID> {

    List<ReconciliationExecutionLogEntity> findTop50ByOrderByStartedAtDesc();
}
