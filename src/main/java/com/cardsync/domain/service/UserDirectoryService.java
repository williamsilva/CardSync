package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.model.UserMinimalModel;
import com.cardsync.domain.model.UserDirectoryEntity;
import com.cardsync.domain.repository.UserDirectoryRepository;
import com.cardsync.infrastructure.nimbusauth.NimbusAuthInternalClient;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve nome/username de um usuário (id emitido pelo NimbusAuth) para exibição em
 * campos de auditoria (createdBy/updatedBy) das respostas da API do Cardsync.
 *
 * Estratégia: cache local (cs_user_directory) + busca sob demanda no NimbusAuth quando
 * o id não está (ou está velho) no cache local, sem fila/evento - "lazy fetch + upsert".
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

  private static final Duration STALE_AFTER = Duration.ofHours(24);

  private final UserDirectoryRepository repository;
  private final NimbusAuthInternalClient client;
  private final Clock clock;

  // condition evita o cache tentar usar uma chave nula quando userId é null (comum em
  // registros de seed, cujo createdBy/updatedBy é intencionalmente null) - o @Cacheable
  // calcula a chave antes de entrar no método, então o null-check abaixo sozinho não evita
  // o erro "Null key returned for cache operation".
  @Cacheable(value = "user-directory", key = "#userId", condition = "#userId != null")
  @Transactional
  public Optional<UserMinimalModel> summaryFor(UUID userId) {
    if (userId == null) {
      return Optional.empty();
    }

    UserDirectoryEntity local = repository.findById(userId).orElse(null);
    OffsetDateTime now = OffsetDateTime.now(clock);

    if (local == null || local.getSyncedAt().isBefore(now.minus(STALE_AFTER))) {
      var remote = client.fetchUsers(java.util.List.of(userId)).stream().findFirst().orElse(null);

      if (remote != null) {
        UserDirectoryEntity toSave = local != null ? local : new UserDirectoryEntity();
        toSave.setId(userId);
        toSave.setUsername(remote.username());
        toSave.setName(remote.name());
        toSave.setSyncedAt(now);
        local = repository.save(toSave);
      }
    }

    if (local == null) {
      return Optional.empty();
    }

    return Optional.of(new UserMinimalModel(local.getId(), local.getName(), local.getUsername()));
  }
}
