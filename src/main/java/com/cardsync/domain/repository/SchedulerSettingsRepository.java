package com.cardsync.domain.repository;

import com.cardsync.domain.model.SchedulerSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SchedulerSettingsRepository extends JpaRepository<SchedulerSettingsEntity, UUID> {

  Optional<SchedulerSettingsEntity> findFirstBy();
}
