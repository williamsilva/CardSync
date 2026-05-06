package com.cardsync.domain.repository;

import com.cardsync.domain.model.AcquirerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
public interface AcquirerRepository extends JpaRepository<AcquirerEntity, UUID>,
  JpaSpecificationExecutor<AcquirerEntity> {

  @Override
  @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
  Page<AcquirerEntity> findAll(Specification<AcquirerEntity> spec, Pageable pageable);

  @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
  @Query("""
    select distinct a
    from AcquirerEntity a
    join a.acquirerCompanies rac
    where rac.company.id = :companyId
    order by a.fantasyName asc, a.socialReason asc
  """)
  List<AcquirerEntity> findAllByCompanyId(@Param("companyId") UUID companyId);

  @Override
  @EntityGraph(attributePaths = {
    "createdBy",
    "updatedBy",
    "acquirerCompanies",
    "acquirerCompanies.company",
    "acquirerEstablishments",
    "acquirerEstablishments.establishment",
    "acquirerEstablishments.establishment.company"
  })
  Optional<AcquirerEntity> findById(UUID id);

  boolean existsByCnpj(String cnpj);

  Optional<AcquirerEntity> findByCnpj(String cnpj);

  Optional<AcquirerEntity> findByFileIdentifierIgnoreCase(String fileIdentifier);

  Optional<AcquirerEntity> findByFantasyNameIgnoreCase(String fantasyName);
}
