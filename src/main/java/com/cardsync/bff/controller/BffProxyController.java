package com.cardsync.bff.controller;

import com.cardsync.bff.service.BffApiClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BffProxyController {

  private final BffApiClient api;

  @GetMapping("/bff/proxy/api-me")
  public ResponseEntity<String> proxyMe(Authentication auth, HttpServletRequest req, HttpServletResponse res) {
    // chama a API local (mais tarde isso vai retornar claims oficiais)
    return api.get(auth, req, res, URI.create("http://localhost:9091/api/v1/me"));
  }
}
