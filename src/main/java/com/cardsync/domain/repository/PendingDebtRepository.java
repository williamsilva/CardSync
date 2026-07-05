package com.cardsync.domain.repository;

import com.cardsync.domain.model.PendingDebtEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PendingDebtRepository extends JpaRepository<PendingDebtEntity, UUID>, JpaSpecificationExecutor<PendingDebtEntity> {

  @Query("select d from PendingDebtEntity d where d.dateDebitOrder >= :lookback order by d.dateDebitOrder asc")
  List<PendingDebtEntity> findAllForDashboard(@Param("lookback") LocalDate lookback);
}
