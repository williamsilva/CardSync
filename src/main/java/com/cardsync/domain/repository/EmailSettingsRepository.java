package com.cardsync.domain.repository;

import com.cardsync.domain.model.EmailSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailSettingsRepository extends JpaRepository<EmailSettingsEntity, UUID> {

  Optional<EmailSettingsEntity> findFirstBy();
}
