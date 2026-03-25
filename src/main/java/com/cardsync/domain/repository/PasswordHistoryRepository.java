package com.cardsync.domain.repository;

import com.cardsync.domain.model.PasswordHistory;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID> {

  List<PasswordHistory> findTop3ByUserIdOrderByCreatedAtDesc(UUID userId);

  // versão paginável para respeitar history-size configurável
  List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
