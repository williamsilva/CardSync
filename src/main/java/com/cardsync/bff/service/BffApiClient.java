package com.cardsync.bff.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class BffApiClient {

  private final BffAccessTokenService accessTokenService;

  private final RestClient rest = RestClient.create();

  public ResponseEntity<String> get(Authentication auth, HttpServletRequest req, HttpServletResponse res, URI uri) {
    String token = accessTokenService.getValidAccessTokenOrRevoke(auth, req, res);
    return rest.get()
      .uri(uri)
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .retrieve()
      .toEntity(String.class);
  }

  /**
   * Encaminha a requisição atual (método, corpo, query string) para outro serviço,
   * trocando o prefixo do path e anexando o access token do usuário logado (Bearer).
   * Usado para expor no Cardsync (via sessão/CSRF) endpoints que agora vivem no NimbusAuth
   * (users/groups/permissions, troca de senha) sem nunca expor o token ao browser.
   */
  public ResponseEntity<byte[]> forwardAuthenticated(
    Authentication auth, HttpServletRequest req, HttpServletResponse res,
    String targetBaseUrl, String fromPrefix, String toPrefix
  ) {
    String token = accessTokenService.getValidAccessTokenOrRevoke(auth, req, res);
    return doForward(req, targetBaseUrl, fromPrefix, toPrefix, HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }

  /**
   * Mesma coisa, mas para endpoints públicos do NimbusAuth (ex: política de senha) -
   * não exige sessão autenticada nem anexa token.
   */
  public ResponseEntity<byte[]> forwardPublic(
    HttpServletRequest req, String targetBaseUrl, String fromPrefix, String toPrefix
  ) {
    return doForward(req, targetBaseUrl, fromPrefix, toPrefix, null, null);
  }

  private ResponseEntity<byte[]> doForward(
    HttpServletRequest req, String targetBaseUrl, String fromPrefix, String toPrefix,
    String extraHeaderName, String extraHeaderValue
  ) {
    String path = req.getRequestURI().substring(req.getContextPath().length());
    String rewrittenPath = toPrefix + path.substring(fromPrefix.length());
    String query = req.getQueryString();

    String url = targetBaseUrl + rewrittenPath + (query != null ? "?" + query : "");

    byte[] body = readBody(req);
    HttpMethod method = HttpMethod.valueOf(req.getMethod());

    RestClient.RequestBodySpec spec = rest.method(method)
      .uri(URI.create(url));

    if (extraHeaderName != null) {
      spec.header(extraHeaderName, extraHeaderValue);
    }

    String contentType = req.getContentType();
    if (contentType != null) {
      spec.header(HttpHeaders.CONTENT_TYPE, contentType);
    }

    if (body.length > 0) {
      spec.body(body);
    }

    return spec.exchange((request, response) -> {
      HttpStatusCode status = response.getStatusCode();
      byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());

      ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
      MediaType responseContentType = response.getHeaders().getContentType();
      if (responseContentType != null) {
        builder.contentType(responseContentType);
      }

      return builder.body(responseBody);
    });
  }

  private byte[] readBody(HttpServletRequest req) {
    try {
      return StreamUtils.copyToByteArray(req.getInputStream());
    } catch (IOException e) {
      log.warn("Falha ao ler corpo da requisição para proxy: {}", e.getMessage());
      return new byte[0];
    }
  }
}
