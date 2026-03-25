package com.cardsync.core.security.resourceserver;

import java.util.List;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final String requiredAudience;

  public AudienceValidator(String requiredAudience) {
    this.requiredAudience = requiredAudience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    List<String> aud = token.getAudience();
    if (aud != null && aud.contains(requiredAudience)) {
      return OAuth2TokenValidatorResult.success();
    }
    OAuth2Error err = new OAuth2Error(
      OAuth2ErrorCodes.INVALID_TOKEN,
      "Invalid audience (aud). Expected: " + requiredAudience,
      null
    );
    return OAuth2TokenValidatorResult.failure(err);
  }
}
