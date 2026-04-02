package com.cardsync.domain.repository;

import com.cardsync.domain.model.AcquirerEntity;
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
public interface AcquirerRepository extends JpaRepository< AcquirerEntity, UUID>,
  JpaSpecificationExecutor<AcquirerEntity> {

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
  Page< AcquirerEntity> findAll(Specification< AcquirerEntity> spec, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
  Optional< AcquirerEntity> findById(UUID id);

  boolean existsByCnpj(String cnpj);
  Optional< AcquirerEntity> findByCnpj(String cnpj);

}
