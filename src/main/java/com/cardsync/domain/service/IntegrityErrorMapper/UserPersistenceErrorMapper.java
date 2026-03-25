package com.cardsync.domain.service.IntegrityErrorMapper;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class UserPersistenceErrorMapper {

  public BusinessException mapSaveError(
    DataIntegrityViolationException ex, String normalizedUserName, String docDigits) {
    ex.getMostSpecificCause();
    String raw = ex.getMostSpecificCause().getMessage();

    String normalized = raw != null ? raw.toLowerCase(Locale.ROOT) : "";

    if (normalized.contains("uk_cs_user_document")
      || normalized.contains("uk_user_document")
      || normalized.contains("document")) {
      return BusinessException.conflict(
        ErrorCode.USER_DOCUMENT_ALREADY_EXISTS,
        "Unique constraint violation for document: " + docDigits
      );
    }

    if (normalized.contains("uk_cs_user_user_name")
      || normalized.contains("uk_user_user_name")
      || normalized.contains("user_name")) {
      return BusinessException.conflict(
        ErrorCode.USER_USERNAME_ALREADY_EXISTS,
        "Unique constraint violation for username/email: " + normalizedUserName
      );
    }

    return BusinessException.conflict(
      ErrorCode.BUSINESS_ERROR,
      raw != null ? raw : "Data integrity violation while saving user"
    );
  }
}
