package com.cardsync.infrastructure.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, UUID> {}
