package com.cardsync.infrastructure.repository;

import com.cardsync.domain.model.FileProcessingExecutionStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FileProcessingExecutionStateRepository extends JpaRepository<FileProcessingExecutionStateEntity, UUID> {

  Optional<FileProcessingExecutionStateEntity> findBySystem(String system);
}
