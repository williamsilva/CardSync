package com.cardsync.domain.repository;

import com.cardsync.domain.model.InviteToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteTokenRepository extends JpaRepository<InviteToken, UUID> {
  Optional<InviteToken> findByTokenHash(String tokenHash);
  List<InviteToken> findAllByUserIdAndUsedAtIsNull(UUID userId);
}
