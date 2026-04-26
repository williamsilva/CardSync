package com.cardsync.domain.repository;

import com.cardsync.domain.model.EstablishmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EstablishmentRepository extends JpaRepository<EstablishmentEntity, UUID>,
  JpaSpecificationExecutor<EstablishmentEntity> {

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy", "company", "acquirer"})
  List<EstablishmentEntity> findAll(Sort sort);

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy", "company", "acquirer"})
  Page<EstablishmentEntity> findAll(Specification<EstablishmentEntity> spec, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy", "company", "acquirer"})
  Optional<EstablishmentEntity> findById(UUID id);

  @EntityGraph(attributePaths = {"createdBy", "updatedBy", "company", "acquirer"})
  List<EstablishmentEntity> findByCompany_IdAndAcquirer_IdOrderByPvNumberAsc(
    UUID companyId, UUID acquirerId);

  boolean existsByPvNumberAndCompany_IdAndAcquirer_Id(
    Integer pvNumber,UUID companyId, UUID acquirerId);

  boolean existsByPvNumberAndCompany_IdAndAcquirer_IdAndIdNot(
    Integer pvNumber, UUID companyId, UUID acquirerId, UUID id);

}
