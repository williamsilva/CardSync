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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolve nome/username de um usuário (id emitido pelo NimbusAuth) para exibição em
 * campos de auditoria (createdBy/updatedBy) das respostas da API do Cardsync.
 *
 * Estratégia: cache local (cs_user_directory) + busca sob demanda no NimbusAuth quando
 * o id não está (ou está velho) no cache local, sem fila/evento - "lazy fetch + upsert".
 *
 * <p>SEM {@code @Cacheable} de propósito (removido em 2026-09-02, mesmo ajuste feito no
 * NimbusFlowServer/NimbusNovaxServer) - havia um cache em memória (ConcurrentMapCacheManager
 * default do Spring Boot, sem TTL/eviction, já que este projeto só tem {@code @EnableCaching}
 * sem nenhum CacheManager customizado) por cima da checagem de staleness abaixo; como nunca
 * expirava dentro da vida do processo, a 1ª resolução de cada usuário ficava congelada pro
 * resto do uptime do container - uma troca de nome no NimbusAuth só refletia depois do
 * próximo restart/deploy. A tabela {@code cs_user_directory} (STALE_AFTER abaixo) já é o
 * único cache que resta.
 */
@Service
@RequiredArgsConstructor
public class UserDirectoryService {

  // Curto de propósito (era 24h) - só pra evitar 1 chamada HTTP por item repetido dentro da
  // MESMA renderização de lista (ex.: 20 registros do mesmo createdBy numa página), não pra
  // servir de cache de longo prazo - 24h fazia uma troca de nome no NimbusAuth demorar até 24h
  // pra aparecer.
  private static final Duration STALE_AFTER = Duration.ofMinutes(1);

  private final UserDirectoryRepository repository;
  private final NimbusAuthInternalClient client;
  private final Clock clock;

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
