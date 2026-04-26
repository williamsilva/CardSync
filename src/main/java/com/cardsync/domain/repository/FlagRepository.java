package com.cardsync.domain.repository;

import com.cardsync.domain.model.FlagEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

  @EntityGraph(attributePaths = {})
  @Query("""
    select distinct f
    from FlagEntity f
    join f.flagCompanies fc
    join f.flagAcquirers fa
    where fc.company.id = :companyId
      and fa.acquirer.id = :acquirerId
    order by f.name asc
  """)
  List<FlagEntity> findAllByCompanyIdAndAcquirerId(
    @Param("companyId") UUID companyId,
    @Param("acquirerId") UUID acquirerId
  );

  boolean existsByNameIgnoreCase(String name);

  Optional<FlagEntity> findByNameIgnoreCase(String name);
}