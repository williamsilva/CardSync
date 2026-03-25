package com.cardsync.core.security.authserver;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Revoke chain" (RFC 9700) simplificado:
 * ao detectar reuse/refresh inválido, removemos TODAS as autorizações do principal para o client.
 *
 * Observação: este approach é forte e simples.
 * Depois, se quiser granularidade (por sessão/device), evoluímos para vincular por sessionId/deviceId.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationRevocationService {

  private final JdbcTemplate jdbc;

  @Transactional
  public int revokeAllForPrincipalAndClient(String principalName, String clientId) {
    // Resolve registered_client_id pelo client_id
    String registeredClientId = jdbc.query(
      "select id from oauth2_registered_client where client_id = ?",
      rs -> rs.next() ? rs.getString(1) : null,
      clientId
    );

    if (registeredClientId == null) {
      return 0;
    }

    // Delete cascata (FK) remove tokens/refresh etc. ligados à autorização
    return jdbc.update(
      "delete from oauth2_authorization where registered_client_id = ? and principal_name = ?",
      registeredClientId, principalName
    );
  }
}
