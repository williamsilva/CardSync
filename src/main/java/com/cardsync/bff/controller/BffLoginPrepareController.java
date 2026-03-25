package com.cardsync.bff.controller;

import com.cardsync.core.security.web.SpaRedirectSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff")
public class BffLoginPrepareController {

  private final SpaRedirectSupport spaRedirectSupport;

  public record LoginPrepareInput(@NotBlank String returnTo) {}

  @PostMapping("/login/prepare")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void prepareLogin(
    @Valid @RequestBody LoginPrepareInput body,
    HttpServletRequest request
  ) {
    var session = request.getSession(true);
    spaRedirectSupport.saveReturnTo(session, body.returnTo());
  }
}