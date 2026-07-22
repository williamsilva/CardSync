package com.cardsync.infrastructure.nimbusauth;

import com.cardsync.core.config.NimbusAuthClientProperties;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP para a API interna (machine-to-machine) do NimbusAuth.
 * Usado para resolver nome/username de usuários (auditoria) e para revogar a
 * cadeia de autorização OAuth2 de um usuário (logout forçado / refresh inválido).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NimbusAuthInternalClient {

  public record UserSummary(UUID id, String username, String name) {}

  private final NimbusAuthClientProperties props;
  private final RestClient.Builder restClientBuilder;

  private RestClient client() {
    return restClientBuilder
      .baseUrl(props.getBaseUrl())
      .defaultHeader("X-Internal-Secret", props.getInternalApiSecret())
      .build();
  }

  public List<UserSummary> fetchUsers(Collection<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }

    try {
      UserSummary[] result = client().get()
        .uri(uriBuilder -> uriBuilder.path("/internal/users")
          .queryParam("ids", ids)
          .build())
        .retrieve()
        .body(UserSummary[].class);

      return result != null ? List.of(result) : List.of();
    } catch (Exception e) {
      log.warn("Falha ao resolver usuários no NimbusAuth: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Pede ao NimbusAuth um dump (pg_dump formato custom) do próprio banco dele. Diferente dos
   * demais métodos desta classe, NÃO degrada silenciosamente em caso de falha — o chamador
   * (BackupService) precisa saber que este alvo específico falhou para reportar no zip final,
   * em vez de produzir um backup incompleto sem avisar.
   */
  public byte[] fetchDatabaseBackup() {
    byte[] result = client().get()
      .uri("/internal/backup/database")
      .retrieve()
      .body(byte[].class);

    if (result == null || result.length == 0) {
      throw new IllegalStateException("NimbusAuth retornou um backup vazio");
    }
    return result;
  }

  public int revokeAuthorization(String principalName, String clientId) {
    try {
      var result = client().post()
        .uri("/internal/oauth2/revoke")
        .body(new RevokeRequest(principalName, clientId))
        .retrieve()
        .body(RevokeResult.class);

      return result != null ? result.revokedCount() : 0;
    } catch (Exception e) {
      log.warn("Falha ao revogar autorização no NimbusAuth: {}", e.getMessage());
      return 0;
    }
  }

  private record RevokeRequest(String principalName, String clientId) {}

  private record RevokeResult(int revokedCount) {}
}
