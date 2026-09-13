package com.cardsync.core.conciliation.analysis;

import com.cardsync.bff.controller.v1.representation.model.conciliation.ErpAcquirerTruthSource;
import com.cardsync.core.file.erp.calculator.InstallmentErpGenerator;
import com.cardsync.core.reconciliation.summary.SalesSummaryTransactionReconciliationService;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.InstallmentErpEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.domain.repository.TransactionErpRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Achado real (auditoria 2026-09-13): reconcileManually(truthSource=ACQUIRER) atualizava
 * erp.installment (total) e os valores agregados, mas nunca regenerava cs_installment_erp -
 * uma venda podia passar a dizer "3x" com uma única parcela real, com o valor da venda inteira.
 * Estes testes cobrem a correção: as parcelas ERP passam a espelhar as parcelas reais da
 * adquirente (valor e data prevista, quando disponível) sempre que ACQUIRER é a fonte da verdade.
 */
class ErpAcquirerResolutionServiceInstallmentTest {

  private final TransactionErpRepository transactionErpRepository = mock(TransactionErpRepository.class);
  private final TransactionAcqRepository transactionAcqRepository = mock(TransactionAcqRepository.class);
  private final SalesSummaryTransactionReconciliationService salesSummaryTransactionReconciliationService =
    mock(SalesSummaryTransactionReconciliationService.class);

  private final ErpAcquirerResolutionService service = new ErpAcquirerResolutionService(
    transactionErpRepository, transactionAcqRepository, salesSummaryTransactionReconciliationService,
    new InstallmentErpGenerator()
  );

  @Test
  void regeneratesErpInstallmentsToMatchAcquirerWhenTotalIncreases() {
    TransactionErpEntity erp = withId(new TransactionErpEntity());
    erp.setInstallment(1);
    erp.setGrossValue(new BigDecimal("300.00"));
    erp.addInstallment(installment(1, "300.00", "294.00", "6.00", LocalDate.of(2026, 10, 1)));

    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    acq.setInstallment(3);
    acq.setGrossValue(new BigDecimal("300.00"));
    addAcqInstallment(acq, acqInstallment(1, "100.00", "98.00", "2.00", LocalDate.of(2026, 10, 1)));
    addAcqInstallment(acq, acqInstallment(2, "100.00", "98.00", "2.00", LocalDate.of(2026, 11, 1)));
    addAcqInstallment(acq, acqInstallment(3, "100.00", "98.00", "2.00", LocalDate.of(2026, 12, 1)));

    when(transactionErpRepository.findForManualResolutionById(erp.getId())).thenReturn(Optional.of(erp));
    when(transactionAcqRepository.findForManualResolutionById(acq.getId())).thenReturn(Optional.of(acq));
    when(transactionErpRepository.findFirstByTransactionAcq_Id(acq.getId())).thenReturn(Optional.empty());
    when(transactionErpRepository.saveAndFlush(erp)).thenReturn(erp);

    service.reconcileManually(erp.getId(), acq.getId(), ErpAcquirerTruthSource.ACQUIRER);

    assertThat(erp.getInstallment()).isEqualTo(3);
    List<InstallmentErpEntity> installments = erp.getInstallments().stream()
      .sorted(Comparator.comparing(InstallmentErpEntity::getInstallment))
      .toList();
    assertThat(installments).hasSize(3);
    assertThat(installments.get(0).getGrossValue()).isEqualByComparingTo("100.00");
    assertThat(installments.get(1).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(installments.get(2).getExpectedPaymentDate()).isEqualTo(LocalDate.of(2026, 12, 1));
  }

  @Test
  void fallsBackToProportionalSplitWhenAcquirerHasNoInstallmentDetail() {
    TransactionErpEntity erp = withId(new TransactionErpEntity());
    erp.setInstallment(1);
    erp.addInstallment(installment(1, "50.00", "49.00", "1.00", LocalDate.of(2026, 10, 1)));

    TransactionAcqEntity acq = withId(new TransactionAcqEntity());
    acq.setInstallment(2);
    acq.setGrossValue(new BigDecimal("150.00"));
    acq.setLiquidValue(new BigDecimal("147.00"));
    acq.setDiscountValue(new BigDecimal("3.00"));
    // sem InstallmentAcqEntity nenhuma - caso defensivo (venda antiga sem detalhamento)

    when(transactionErpRepository.findForManualResolutionById(erp.getId())).thenReturn(Optional.of(erp));
    when(transactionAcqRepository.findForManualResolutionById(acq.getId())).thenReturn(Optional.of(acq));
    when(transactionErpRepository.findFirstByTransactionAcq_Id(acq.getId())).thenReturn(Optional.empty());
    when(transactionErpRepository.saveAndFlush(erp)).thenReturn(erp);

    service.reconcileManually(erp.getId(), acq.getId(), ErpAcquirerTruthSource.ACQUIRER);

    assertThat(erp.getInstallment()).isEqualTo(2);
    assertThat(erp.getInstallments()).hasSize(2);
    BigDecimal totalGross = erp.getInstallments().stream()
      .map(InstallmentErpEntity::getGrossValue)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(totalGross).isEqualByComparingTo("150.00");
  }

  private InstallmentErpEntity installment(int number, String gross, String liquid, String discount, LocalDate expectedPaymentDate) {
    InstallmentErpEntity installment = new InstallmentErpEntity();
    installment.setInstallment(number);
    installment.setGrossValue(new BigDecimal(gross));
    installment.setLiquidValue(new BigDecimal(liquid));
    installment.setDiscountValue(new BigDecimal(discount));
    installment.setExpectedPaymentDate(expectedPaymentDate);
    return installment;
  }

  private void addAcqInstallment(TransactionAcqEntity acq, InstallmentAcqEntity installment) {
    installment.setTransaction(acq);
    acq.getInstallments().add(installment);
  }

  private InstallmentAcqEntity acqInstallment(int number, String gross, String liquid, String discount, LocalDate expectedPaymentDate) {
    InstallmentAcqEntity installment = new InstallmentAcqEntity();
    installment.setInstallment(number);
    installment.setGrossValue(new BigDecimal(gross));
    installment.setLiquidValue(new BigDecimal(liquid));
    installment.setDiscountValue(new BigDecimal(discount));
    installment.setExpectedPaymentDate(expectedPaymentDate);
    return installment;
  }

  private <T extends AuditableEntityBase> T withId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
