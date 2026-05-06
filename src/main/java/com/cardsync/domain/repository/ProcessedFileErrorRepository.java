package com.cardsync.domain.repository;

import com.cardsync.domain.model.ProcessedFileErrorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProcessedFileErrorRepository extends JpaRepository<ProcessedFileErrorEntity, UUID> {
  List<ProcessedFileErrorEntity> findByProcessedFile_IdOrderByLineNumberAsc(UUID processedFileId);
}
