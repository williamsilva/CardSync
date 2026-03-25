package com.cardsync.bff.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apenas para garantir que o cookie XSRF-TOKEN seja emitido (Double Submit).
 * O CsrfCookieFilter já força isso ao tocar no token, mas este endpoint facilita a SPA.
 */
@RestController
public class BffCsrfController {

  @GetMapping("/bff/csrf")
  public ResponseEntity<Void> csrf() {
    return ResponseEntity.noContent().build();
  }
}
