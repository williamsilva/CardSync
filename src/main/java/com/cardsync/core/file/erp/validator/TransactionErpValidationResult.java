package com.cardsync.core.file.erp.validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TransactionErpValidationResult {
  private final List<TransactionErpValidationError> errors = new ArrayList<>();
  private final List<TransactionErpValidationError> warnings = new ArrayList<>();

  public void addError(TransactionErpValidationError error) {
    errors.add(error);
  }

  public void addWarning(TransactionErpValidationError warning) {
    warnings.add(warning);
  }

  public boolean isValid() {
    return errors.isEmpty();
  }

  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }

  public List<TransactionErpValidationError> errors() {
    return Collections.unmodifiableList(errors);
  }

  public List<TransactionErpValidationError> warnings() {
    return Collections.unmodifiableList(warnings);
  }
}
