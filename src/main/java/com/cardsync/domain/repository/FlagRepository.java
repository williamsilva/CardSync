package com.cardsync.domain.repository;

import com.cardsync.domain.model.FlagEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlagRepository extends JpaRepository<FlagEntity, UUID>, JpaSpecificationExecutor<FlagEntity> {

  @EntityGraph(attributePaths = {
    "flagCompanies",
    "flagCompanies.company",
    "flagAcquirers",
    "flagAcquirers.acquirer"
  })
  Optional<FlagEntity> findDetailedById(UUID id);

  boolean existsByNameIgnoreCase(String name);

  Optional<FlagEntity> findByNameIgnoreCase(String name);
}