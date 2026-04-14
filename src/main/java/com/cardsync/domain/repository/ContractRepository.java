package com.cardsync.domain.repository;

import com.cardsync.domain.model.ContractEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<ContractEntity, UUID>,
  JpaSpecificationExecutor<ContractEntity> {

  @Override
  @EntityGraph(attributePaths = {"establishment", "company", "acquirer"})
  Page<ContractEntity> findAll(Specification<ContractEntity> spec, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"establishment", "company", "acquirer"})
  Optional<ContractEntity> findById(UUID id);

}