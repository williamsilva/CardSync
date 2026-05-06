package com.cardsync.domain.repository;

import com.cardsync.domain.model.ContractEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractRepository extends JpaRepository<ContractEntity, UUID>,
  JpaSpecificationExecutor<ContractEntity> {

  @Override
  @EntityGraph(attributePaths = {
    "company", "acquirer", "establishment", "createdBy", "updatedBy",
    "contractFlags", "contractFlags.flag", "contractFlags.contractRates"})
  Page<ContractEntity> findAll(Specification<ContractEntity> spec, Pageable pageable);

  @EntityGraph(attributePaths = {
    "company", "acquirer", "establishment", "createdBy", "updatedBy",
    "contractFlags", "contractFlags.flag", "contractFlags.contractRates"
  })
  @Query("select c from ContractEntity c where c.id = :id")
  Optional<ContractEntity> findDetailedById(@Param("id") UUID id);

  @EntityGraph(attributePaths = {
    "company", "acquirer", "establishment",
    "contractFlags", "contractFlags.flag", "contractFlags.contractRates"
  })
  @Query("""
    select c
    from ContractEntity c
    join c.contractFlags cf
    join cf.contractRates cr
    where c.status = :status
      and c.acquirer.id = :acquirerId
      and cf.flag.id = :flagId
      and cr.modality = :modality
      and c.startDate <= :saleDate
      and (c.endDate is null or c.endDate >= :saleDate)
      and (
        (:companyId is null and c.company is null)
        or (:companyId is not null and (c.company is null or c.company.id = :companyId))
      )
      and (
        (:establishmentId is null and c.establishment is null)
        or (:establishmentId is not null and (c.establishment is null or c.establishment.id = :establishmentId))
      )
    order by
      case when c.establishment is not null then 0 else 1 end,
      case when c.company is not null then 0 else 1 end,
      c.startDate desc
    """)
  List<ContractEntity> findCandidatesForErpRate(
    @Param("companyId") UUID companyId,
    @Param("acquirerId") UUID acquirerId,
    @Param("establishmentId") UUID establishmentId,
    @Param("flagId") UUID flagId,
    @Param("modality") Integer modality,
    @Param("status") Integer status,
    @Param("saleDate") LocalDate saleDate
  );

  @Query("""
    select case when count(c) > 0 then true else false end
    from ContractEntity c
    where c.acquirer.id = :acquirerId
      and ((:companyId is null and c.company is null) or c.company.id = :companyId)
      and ((:establishmentId is null and c.establishment is null) or c.establishment.id = :establishmentId)
      and (:currentId is null or c.id <> :currentId)
       and c.status = :status
    """)
  boolean existsDuplicate(
    @Param("companyId") UUID companyId,
    @Param("acquirerId") UUID acquirerId,
    @Param("establishmentId") UUID establishmentId,
    @Param("currentId") UUID currentId,
    @Param("status") Integer status
  );
}
