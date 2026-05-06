package com.cardsync.domain.repository;

import com.cardsync.domain.model.TransactionAcqEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionAcqRepository extends JpaRepository<TransactionAcqEntity, UUID> {
  Optional<TransactionAcqEntity> findFirstByNsuAndAuthorization(Long nsu, String authorization);
  Optional<TransactionAcqEntity> findFirstByNsu(Long nsu);
  Optional<TransactionAcqEntity> findFirstByAuthorization(String authorization);
}
