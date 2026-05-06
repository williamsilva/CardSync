package com.cardsync.domain.repository;

import com.cardsync.domain.model.CompanyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<CompanyEntity, UUID>,
  JpaSpecificationExecutor<CompanyEntity> {

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
  Page<CompanyEntity> findAll(Specification<CompanyEntity> spec, Pageable pageable);

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
  Optional<CompanyEntity> findById(UUID id);

  boolean existsByCnpj(String cnpj);
  Optional<CompanyEntity> findByCnpj(String cnpj);
  Optional<CompanyEntity> findByFantasyNameIgnoreCase(String fantasyName);
  Optional<CompanyEntity> findBySocialReasonIgnoreCase(String socialReason);


  @Query("""
    select c
      from CompanyEntity c
     where lower(c.fantasyName) like lower(concat('%', :name, '%'))
        or lower(c.socialReason) like lower(concat('%', :name, '%'))
     order by
       case when lower(c.fantasyName) = lower(:name) then 0 else 1 end,
       c.fantasyName asc
  """)
  java.util.List<CompanyEntity> lookupByName(@Param("name") String name, org.springframework.data.domain.Pageable pageable);

}
