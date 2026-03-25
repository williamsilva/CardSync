package com.cardsync.web.controller;

import com.cardsync.core.security.CardsyncSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

  private final CardsyncSecurityProperties props;

  @GetMapping("/")
  public String home() {
    // Em DEV, redireciona para o SPA (Angular). Em PROD, usar domínio do front.
    return "redirect:" + props.getWeb().getSpaBaseUrl();
  }

  @GetMapping("/success")
  public String success() {
    // Página de diagnóstico/manual (não deve ser o target normal do login)
    return "success";
  }
}
