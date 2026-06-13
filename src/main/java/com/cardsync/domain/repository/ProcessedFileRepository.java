package com.cardsync.domain.repository;

import com.cardsync.domain.model.OriginFileEntity;
import com.cardsync.domain.model.ProcessedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedFileRepository extends JpaRepository<ProcessedFileEntity, UUID>,
  JpaSpecificationExecutor<ProcessedFileEntity> {

  Optional<ProcessedFileEntity> findFirstByContentHash(String contentHash);

  Optional<ProcessedFileEntity> findFirstByFileAndOriginFile(String file, OriginFileEntity originFile);

  @Query("""
    select pf
      from ProcessedFileEntity pf
      left join fetch pf.originFile
     where pf.dateFile between :startDate and :endDate
     order by pf.dateFile asc, pf.file asc
    """)
    List<ProcessedFileEntity> findCalendarFiles(
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate
  );

}