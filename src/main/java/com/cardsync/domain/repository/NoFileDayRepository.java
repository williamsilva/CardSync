package com.cardsync.domain.repository;

import com.cardsync.domain.model.NoFileDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface NoFileDayRepository extends JpaRepository<NoFileDayEntity, UUID>, JpaSpecificationExecutor<NoFileDayEntity> {

  List<NoFileDayEntity> findAllByNoFileDateBetweenOrderByNoFileDateAsc(
    LocalDate startDate,
    LocalDate endDate
  );

}