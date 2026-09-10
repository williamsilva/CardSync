package com.cardsync.core.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protege as rotas /internal/backup/** (chamadas pelo NimbusAuth pra puxar o backup deste
 *  servidor) - reaproveita o MESMO secret já configurado em NimbusAuthClientProperties
 *  (NIMBUS_INTERNAL_API_SECRET), usado hoje só pra CHAMAR o NimbusAuth (ver
 *  NimbusAuthInternalClient) - o valor já é idêntico em todos os apps Nimbus no Railway, então
 *  reaproveitá-lo aqui não exige nenhuma env var nova (ver NimbusAuthProxyProperties, mesmo
 *  comentário no repo NimbusFlow/NimbusDesk/NimbusNovax). */
public class InternalBackupSecretFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Internal-Secret";

  private final String expectedSecret;

  public InternalBackupSecretFilter(String expectedSecret) {
    this.expectedSecret = expectedSecret;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
  ) throws ServletException, IOException {

    String provided = request.getHeader(HEADER);

    if (expectedSecret == null || expectedSecret.isBlank() || provided == null || !secretsMatch(expectedSecret, provided)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    filterChain.doFilter(request, response);
  }

  // Comparação constant-time - evita timing attack pra descobrir o secret byte a byte.
  private boolean secretsMatch(String expected, String provided) {
    return MessageDigest.isEqual(
      expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)
    );
  }
}
