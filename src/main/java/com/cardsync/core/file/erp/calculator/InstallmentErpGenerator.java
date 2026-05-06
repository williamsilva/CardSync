package com.cardsync.core.file.erp.calculator;

import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.model.enums.StatusPaymentEnum;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class InstallmentErpGenerator {

  public List<InstallmentErpEntity> generate(TransactionErpEntity transaction, Integer contractedPaymentTermDays) {
    int totalInstallments = Math.max(transaction.getInstallment() == null ? 1 : transaction.getInstallment(), 1);
    BigDecimal grossTotal = nvl(transaction.getGrossValue());
    BigDecimal liquidTotal = nvl(transaction.getLiquidValue());
    BigDecimal discountTotal = nvl(transaction.getDiscountValue());

    BigDecimal grossPerInstallment = grossTotal.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.DOWN);
    BigDecimal liquidPerInstallment = liquidTotal.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.DOWN);
    BigDecimal discountPerInstallment = discountTotal.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.DOWN);

    BigDecimal grossRemainder = grossTotal.subtract(grossPerInstallment.multiply(BigDecimal.valueOf(totalInstallments)));
    BigDecimal liquidRemainder = liquidTotal.subtract(liquidPerInstallment.multiply(BigDecimal.valueOf(totalInstallments)));
    BigDecimal discountRemainder = discountTotal.subtract(discountPerInstallment.multiply(BigDecimal.valueOf(totalInstallments)));

    List<InstallmentErpEntity> installments = new ArrayList<>();
    for (int i = 1; i <= totalInstallments; i++) {
      InstallmentErpEntity installment = new InstallmentErpEntity();
      installment.setInstallment(i);
      installment.setPaymentStatus(StatusPaymentEnum.PENDING.getCode());
      installment.setInstallmentStatus(StatusInstallmentEnum.SCHEDULED.getCode());
      installment.setCreditDate(calculateCreditDate(transaction, i, contractedPaymentTermDays));
      installment.setGrossValue(i == 1 ? grossPerInstallment.add(grossRemainder) : grossPerInstallment);
      installment.setLiquidValue(i == 1 ? liquidPerInstallment.add(liquidRemainder) : liquidPerInstallment);
      installment.setDiscountValue(i == 1 ? discountPerInstallment.add(discountRemainder) : discountPerInstallment);
      installment.setTransaction(transaction);
      installments.add(installment);
    }
    return installments;
  }

  private LocalDate calculateCreditDate(TransactionErpEntity transaction, int installmentNumber, Integer contractedPaymentTermDays) {
    LocalDate saleDate = transaction.getSaleDate() == null ? LocalDate.now() : transaction.getSaleDate().toLocalDate();
    if (contractedPaymentTermDays != null && contractedPaymentTermDays >= 0) {
      return saleDate.plusDays(contractedPaymentTermDays.longValue() * installmentNumber);
    }
    if (transaction.getModality() != null && transaction.getModality().equals(ModalityEnum.CASH_DEBIT.getCode())) {
      return saleDate.plusDays(1);
    }
    return saleDate.plusDays(30L * installmentNumber);
  }

  private BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
