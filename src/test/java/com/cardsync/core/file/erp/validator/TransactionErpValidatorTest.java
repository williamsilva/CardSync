package com.cardsync.core.file.erp.validator;

import com.cardsync.core.file.erp.dto.TransactionErpCsvDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionErpValidatorTest {

  private final TransactionErpValidator validator = new TransactionErpValidator();

  @Test
  void shouldAcceptValidPaymentRowWithOnlyWarningsForMissingIdentifiers() {
    TransactionErpCsvDto dto = new TransactionErpCsvDto();
    dto.setTransaction("Pagamento");
    dto.setSaleDate(OffsetDateTime.parse("2026-04-10T10:00:00Z"));
    dto.setGrossValue(new BigDecimal("100.00"));
    dto.setAcquirer("Rede");
    dto.setFlag("Visa");
    dto.setInstallment(1);

    var result = validator.validate(dto);

    assertThat(result.isValid()).isTrue();
    assertThat(result.warnings()).extracting(TransactionErpValidationError::code)
      .contains("ERP_IDENTIFIERS_MISSING", "ERP_COMPANY_CONTEXT_MISSING", "ERP_ESTABLISHMENT_CONTEXT_MISSING");
  }

  @Test
  void shouldRejectMissingRequiredFields() {
    TransactionErpCsvDto dto = new TransactionErpCsvDto();
    dto.setTransaction("Pagamento");
    dto.setGrossValue(BigDecimal.ZERO);

    var result = validator.validate(dto);

    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).extracting(TransactionErpValidationError::code)
      .contains("ERP_SALE_DATE_REQUIRED", "ERP_GROSS_VALUE_REQUIRED", "ERP_ACQUIRER_REQUIRED", "ERP_FLAG_REQUIRED");
  }

  @Test
  void shouldIgnoreNonPaymentRows() {
    assertThat(validator.isPaymentType("Cancelamento")).isFalse();
    assertThat(validator.isPaymentType("Pagamento manual")).isTrue();
    assertThat(validator.isPaymentType("Pagamento")).isTrue();
  }
}
