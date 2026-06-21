package com.cardsync.domain.repository;

import com.cardsync.domain.model.ReconciliationSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReconciliationSettingsRepository extends JpaRepository<ReconciliationSettingsEntity, UUID> {

  Optional<ReconciliationSettingsEntity> findFirstBy();
}
