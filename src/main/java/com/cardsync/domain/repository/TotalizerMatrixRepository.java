package com.cardsync.domain.repository;

import com.cardsync.domain.model.TotalizerMatrixEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TotalizerMatrixRepository extends JpaRepository<TotalizerMatrixEntity, UUID>, JpaSpecificationExecutor<TotalizerMatrixEntity> {
}
