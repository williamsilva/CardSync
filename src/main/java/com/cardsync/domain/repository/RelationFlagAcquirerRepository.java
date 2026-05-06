package com.cardsync.domain.repository;

import com.cardsync.domain.model.RelationFlagAcquirerEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RelationFlagAcquirerRepository extends JpaRepository<RelationFlagAcquirerEntity, UUID> {
  @EntityGraph(attributePaths = {"flag", "acquirer"})
  Optional<RelationFlagAcquirerEntity> findByAcquirer_IdAndAcquirerCode(UUID acquirerId, String acquirerCode);
}
