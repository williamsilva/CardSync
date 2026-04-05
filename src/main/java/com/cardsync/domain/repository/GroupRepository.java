package com.cardsync.domain.repository;

import com.cardsync.domain.model.GroupEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GroupRepository extends JpaRepository<GroupEntity, UUID>, JpaSpecificationExecutor<GroupEntity> {

  List<GroupEntity> findAllByNameNotIgnoreCaseOrderByNameAsc(String name);

  boolean existsByNameIgnoreCase(String name);
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
  Page<GroupEntity> findAll(Specification< GroupEntity> spec, Pageable pageable);

  @EntityGraph(attributePaths = {"permissions", "users", "createdBy", "updatedBy"})
  Optional<GroupEntity> findDetailedById(UUID id);

  @EntityGraph(attributePaths = {"permissions", "users", "createdBy", "updatedBy"})
  List<GroupEntity> findDetailedByIdIn(Collection<UUID> ids);
}
