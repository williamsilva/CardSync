package com.cardsync.domain.repository;

import com.cardsync.domain.model.BankEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BankRepository extends JpaRepository<BankEntity, UUID>, JpaSpecificationExecutor<BankEntity> {
  Optional<BankEntity> findByCode(String code);
  Optional<BankEntity> findByCodeIgnoreCase(String code);
}
