package com.cardsync.domain.repository;

import com.cardsync.domain.model.OriginFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OriginFileRepository extends JpaRepository<OriginFileEntity, UUID> {
  Optional<OriginFileEntity> findByCodeIgnoreCase(String code);
}
