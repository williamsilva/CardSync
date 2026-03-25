package com.cardsync.domain.repository;

import com.cardsync.domain.model.EmailLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmailLogRepository extends JpaRepository<EmailLogEntity, UUID>, JpaSpecificationExecutor<EmailLogEntity> {
}