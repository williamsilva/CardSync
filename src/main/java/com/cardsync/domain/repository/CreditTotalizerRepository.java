package com.cardsync.domain.repository;

import com.cardsync.domain.model.CreditTotalizerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CreditTotalizerRepository extends JpaRepository<CreditTotalizerEntity, UUID>, JpaSpecificationExecutor<CreditTotalizerEntity> {
}
