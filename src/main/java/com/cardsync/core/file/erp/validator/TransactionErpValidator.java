package com.cardsync.core.file.erp.validator;

import com.cardsync.core.file.erp.dto.TransactionErpCsvDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionErpValidator {

  public TransactionErpValidationResult validate(TransactionErpCsvDto dto) {
    TransactionErpValidationResult result = new TransactionErpValidationResult();

    if (dto == null) {
      result.addError(TransactionErpValidationError.validation("ERP_ROW_NULL", "Linha vazia ou ilegível."));
      return result;
    }

    if (isBlank(dto.getTransaction())) {
      result.addError(TransactionErpValidationError.validation("ERP_TRANSACTION_TYPE_REQUIRED", "Tipo da transação é obrigatório."));
    } else if (!isPaymentType(dto.getTransaction())) {
      result.addWarning(TransactionErpValidationError.validation(
        "ERP_TRANSACTION_TYPE_IGNORED",
        "Tipo da transação ignorado: " + dto.getTransaction()
      ));
    }

    if (dto.getSaleDate() == null) {
      result.addError(TransactionErpValidationError.validation("ERP_SALE_DATE_REQUIRED", "Data da venda é obrigatória ou inválida."));
    }

    if (dto.getGrossValue() == null || dto.getGrossValue().compareTo(BigDecimal.ZERO) <= 0) {
      result.addError(TransactionErpValidationError.validation("ERP_GROSS_VALUE_REQUIRED", "Valor bruto deve ser maior que zero."));
    }

    if (isBlank(dto.getAcquirer())) {
      result.addError(TransactionErpValidationError.validation("ERP_ACQUIRER_REQUIRED", "Adquirente é obrigatória."));
    }

    if (isBlank(dto.getFlag())) {
      result.addError(TransactionErpValidationError.validation("ERP_FLAG_REQUIRED", "Bandeira é obrigatória."));
    }

    if (dto.getInstallment() != null && dto.getInstallment() < 1) {
      result.addError(TransactionErpValidationError.validation("ERP_INSTALLMENT_INVALID", "Quantidade de parcelas deve ser maior ou igual a 1."));
    }

    if (dto.getNsu() == null && isBlank(dto.getAuthorization()) && isBlank(dto.getTid())) {
      result.addWarning(TransactionErpValidationError.validation(
        "ERP_IDENTIFIERS_MISSING",
        "Linha sem NSU, autorização e TID; conciliação futura pode ficar imprecisa."
      ));
    }

    if (isBlank(dto.getCompanyCnpj()) && isBlank(dto.getCompanyName())) {
      result.addWarning(TransactionErpValidationError.validation(
        "ERP_COMPANY_CONTEXT_MISSING",
        "CSV não informou CNPJ/nome da empresa; contrato pode ser localizado apenas por regra genérica."
      ));
    }

    if (dto.getEstablishmentPvNumber() == null) {
      result.addWarning(TransactionErpValidationError.validation(
        "ERP_ESTABLISHMENT_CONTEXT_MISSING",
        "CSV não informou PV/estabelecimento; contrato pode ser localizado apenas por empresa ou regra genérica."
      ));
    }

    return result;
  }

  public boolean isValid(TransactionErpCsvDto dto) {
    return validate(dto).isValid();
  }

  public boolean isPaymentType(String transactionType) {
    if (transactionType == null) return false;
    String normalized = transactionType.trim();
    return "Pagamento manual".equalsIgnoreCase(normalized)
      || "Pagamento".equalsIgnoreCase(normalized);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
