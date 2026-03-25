package com.cardsync.bff.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
}
