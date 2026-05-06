package com.cardsync.domain.repository;

import com.cardsync.domain.model.RedeSuspendedPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RedeSuspendedPaymentRepository extends JpaRepository<RedeSuspendedPaymentEntity, UUID> {
}
