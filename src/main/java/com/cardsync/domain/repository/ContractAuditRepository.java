package com.cardsync.domain.repository;

import com.cardsync.domain.model.ContractAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractAuditRepository extends JpaRepository<ContractAuditEntity, UUID>, JpaSpecificationExecutor<ContractAuditEntity> {

  List<ContractAuditEntity> findByTransactionAcq_IdIn(Collection<UUID> transactionAcqIds);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from ContractAuditEntity audit where audit.transactionAcq.id in :transactionAcqIds")
  int deleteByTransactionAcqIdInBulk(@Param("transactionAcqIds") Collection<UUID> transactionAcqIds);
}
