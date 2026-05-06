package com.cardsync.domain.repository;

import com.cardsync.domain.model.ProcessedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedFileRepository extends JpaRepository<ProcessedFileEntity, UUID> {
  Optional<ProcessedFileEntity> findByFileAndOriginFile_CodeIgnoreCase(String file, String originCode);
  boolean existsByFileAndOriginFile_CodeIgnoreCase(String file, String originCode);
}
