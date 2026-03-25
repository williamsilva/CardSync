package com.cardsync.web.controller;

import com.cardsync.core.security.password.PasswordExpiryService;
import com.cardsync.core.security.password.PasswordExpiryViewModel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class PasswordStatusWebController {

  private final PasswordExpiryService passwordExpiryService;

  @PostMapping(
    value = "/login/password/status",
    consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
  )
  @ResponseBody
  public PasswordExpiryViewModel status(@RequestParam(value = "username", required = false) String username) {
    // 🔐 Não vaza existência:
    // Service já retorna (false,0,0) quando não acha usuário.
    if (username == null || username.isBlank()) {
      return new PasswordExpiryViewModel(false, 0L, 0L);
    }
    return passwordExpiryService.statusForUsername(username.trim());
  }
}