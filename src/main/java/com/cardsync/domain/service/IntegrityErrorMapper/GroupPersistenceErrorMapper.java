package com.cardsync.domain.service.IntegrityErrorMapper;

import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class GroupPersistenceErrorMapper {

  public BusinessException mapSaveError(
    DataIntegrityViolationException ex, String normalizedName) {
    ex.getMostSpecificCause();
    String raw = ex.getMostSpecificCause().getMessage();

    String normalized = raw != null ? raw.toLowerCase(Locale.ROOT) : "";

    if (normalized.contains("name")) {
      return BusinessException.conflict(
        ErrorCode.GROUP_NAME_ALREADY_EXISTS,
        "Unique constraint violation for name: " + normalizedName
      );
    }

    return BusinessException.conflict(
      ErrorCode.BUSINESS_ERROR,
      raw != null ? raw : "Data integrity violation while saving group"
    );
  }
}
