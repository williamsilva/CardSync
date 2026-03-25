package com.cardsync.domain.repository;

import com.cardsync.domain.model.ResetToken;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResetTokenRepository extends JpaRepository<ResetToken, UUID> {

  Optional<ResetToken> findByTokenHash(String tokenHash);

  List<ResetToken> findAllByUserIdAndUsedAtIsNull(UUID userId);

  long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, OffsetDateTime createdAt);
}
