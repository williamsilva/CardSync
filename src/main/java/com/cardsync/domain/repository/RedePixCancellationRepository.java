package com.cardsync.domain.repository;

import com.cardsync.domain.model.RedePixCancellationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RedePixCancellationRepository extends JpaRepository<RedePixCancellationEntity, UUID> {
}
