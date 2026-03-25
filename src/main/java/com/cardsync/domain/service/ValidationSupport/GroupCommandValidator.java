package com.cardsync.domain.service.ValidationSupport;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupCommandValidator {

  public String requireName(String value) {
    String email = value == null ? "" : value.trim().toLowerCase();

    if (email.isBlank()) {
      throw BusinessException.badRequest(
        ErrorCode.INVALID_EMAIL,
        "E-mail is required"
      );
    }

    return email;
  }

  public String requireTrim(String value, String field) {
    String v = value == null ? "" : value.trim();
    if (!v.isBlank()) {
      return v;
    }

    ErrorCode code = switch (field) {
      case "name" -> ErrorCode.NAME_REQUIRED;
      default -> ErrorCode.VALIDATION_ERROR;
    };

    throw BusinessException.badRequest(
      code,
      "Required field is blank: " + field
    );
  }
}
