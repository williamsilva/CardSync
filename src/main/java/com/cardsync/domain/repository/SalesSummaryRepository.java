package com.cardsync.domain.repository;

import com.cardsync.domain.model.SalesSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalesSummaryRepository extends JpaRepository<SalesSummaryEntity, UUID> {

  Optional<SalesSummaryEntity> findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(
    UUID acquirerId,
    Integer pvNumber,
    Integer rvNumber
  );
}
