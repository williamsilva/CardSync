package com.cardsync.bff.controller;

import com.cardsync.core.security.web.SpaRedirectSupport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class BffLoginController {

  private final SpaRedirectSupport spaRedirectSupport;

  @GetMapping("/bff/login")
  public String login(HttpServletRequest request, Authentication authentication) {
    if (authentication instanceof OAuth2AuthenticationToken token && token.isAuthenticated()) {
      String redirectTo = spaRedirectSupport.normalizeForRedirect(
        spaRedirectSupport.consumeReturnTo(request.getSession(false))
      );

      if (redirectTo != null) {
        return "redirect:" + redirectTo;
      }

      return "redirect:" + spaRedirectSupport.defaultSpaTarget();
    }

    return "redirect:/oauth2/authorization/cardsync-bff";
  }
}
