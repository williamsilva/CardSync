package com.cardsync.domain.repository;

import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ErpCommercialStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionErpRepository extends JpaRepository<TransactionErpEntity, UUID>, JpaSpecificationExecutor<TransactionErpEntity> {

  Page<TransactionErpEntity> findByCommercialStatusIn(Collection<ErpCommercialStatusEnum> statuses, Pageable pageable);
  Optional<TransactionErpEntity> findByIdAndCommercialStatusIn(UUID id, Collection<ErpCommercialStatusEnum> statuses);
  List<TransactionErpEntity> findTop500ByCommercialStatusInOrderBySaleDateAsc(Collection<ErpCommercialStatusEnum> statuses);
}
