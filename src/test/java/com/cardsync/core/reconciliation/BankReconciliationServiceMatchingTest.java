package com.cardsync.core.reconciliation;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.AuditableEntityBase;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes diretos (sem contexto Spring) da lógica de matching de
 * {@link BankReconciliationService} — instanciado via construtor com todas as
 * dependências nulas, já que os métodos cobertos aqui só operam sobre os
 * parâmetros recebidos, nunca sobre os campos injetados.
 */
class BankReconciliationServiceMatchingTest {

  private final BankReconciliationService service =
    new BankReconciliationService(null, null, null, null, null, null, null, null, null);

  // ── paymentKindFromModality — base da Fase 4 (DIGITAL_WALLET/OUTROS → CREDIT) ──

  @Test
  void cashDebitMapsToDebit() {
    assertThat(service.paymentKindFromModality(ModalityEnum.CASH_DEBIT.getCode()))
      .isEqualTo(ReconciliationMatchContext.PaymentKind.DEBIT);
  }

  @Test
  void creditCardModalitiesMapToCredit() {
    assertThat(service.paymentKindFromModality(ModalityEnum.CASH_CREDIT.getCode()))
      .isEqualTo(ReconciliationMatchContext.PaymentKind.CREDIT);
    assertThat(service.paymentKindFromModality(ModalityEnum.INSTALLMENT_CREDIT_2_6.getCode()))
      .isEqualTo(ReconciliationMatchContext.PaymentKind.CREDIT);
    assertThat(service.paymentKindFromModality(ModalityEnum.INSTALLMENT_CREDIT_7_12.getCode()))
      .isEqualTo(ReconciliationMatchContext.PaymentKind.CREDIT);
    assertThat(service.paymentKindFromModality(ModalityEnum.INSTALLMENT_CREDIT_13_21.getCode()))
      .isEqualTo(ReconciliationMatchContext.PaymentKind.CREDIT);
  }

  @Test
  void digitalWalletAndOutrosMapToCreditNotUnknown() {
    assertThat(service.paymentKindFromModality(ModalityEnum.DIGITAL_WALLET.getCode()))
      .isEqualTo(ReconciliationMatchContext.PaymentKind.CREDIT);
    assertThat(service.paymentKindFromModality(ModalityEnum.OUTROS.getCode()))
      .isEqualTo(ReconciliationMatchContext.PaymentKind.CREDIT);
  }

  @Test
  void nullOrUnmappedModalityIsUnknown() {
    assertThat(service.paymentKindFromModality(null))
      .isEqualTo(ReconciliationMatchContext.PaymentKind.UNKNOWN);
    assertThat(service.paymentKindFromModality(ModalityEnum.NULL.getCode()))
      .isEqualTo(ReconciliationMatchContext.PaymentKind.UNKNOWN);
  }

  // ── contextOf(CreditOrderEntity) — establishmentPv vem de pvCentralizer ────────

  @Test
  void creditOrderContextUsesPvCentralizerAsEstablishmentPvAndHasNoEstablishmentId() {
    CreditOrderEntity order = new CreditOrderEntity();
    order.setCompany(entityWithId(new CompanyEntity()));
    order.setAcquirer(entityWithId(new AcquirerEntity()));
    order.setFlag(entityWithId(new FlagEntity()));
    order.setPvCentralizer(12345);

    SalesSummaryEntity summary = new SalesSummaryEntity();
    summary.setModality(ModalityEnum.CASH_CREDIT.getCode());
    order.setSalesSummary(summary);

    ReconciliationMatchContext context = service.contextOf(order);

    assertThat(context.establishmentId()).isNull();
    assertThat(context.establishmentPv()).isEqualTo(12345);
    assertThat(context.paymentKind()).isEqualTo(ReconciliationMatchContext.PaymentKind.CREDIT);
    assertThat(context.companyId()).isEqualTo(order.getCompany().getId());
    assertThat(context.acquirerId()).isEqualTo(order.getAcquirer().getId());
    assertThat(context.flagId()).isEqualTo(order.getFlag().getId());
  }

  @Test
  void creditOrderContextHasNullEstablishmentPvWhenPvCentralizerIsNull() {
    CreditOrderEntity order = new CreditOrderEntity();
    order.setCompany(entityWithId(new CompanyEntity()));
    order.setAcquirer(entityWithId(new AcquirerEntity()));

    assertThat(service.contextOf(order).establishmentPv()).isNull();
  }

  // ── contextOf(ReleasesBankEntity) — establishmentPv vem de establishment.pvNumber ──

  @Test
  void releaseContextUsesEstablishmentPvNumber() {
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setCompany(entityWithId(new CompanyEntity()));
    release.setAcquirer(entityWithId(new AcquirerEntity()));
    release.setFlag(entityWithId(new FlagEntity()));
    release.setModalityPaymentBank(ModalityPaymentBankEnum.CASH_CREDIT);

    EstablishmentEntity establishment = entityWithId(new EstablishmentEntity());
    establishment.setPvNumber(98765);
    release.setEstablishment(establishment);

    ReconciliationMatchContext context = service.contextOf(release);

    assertThat(context.establishmentId()).isEqualTo(establishment.getId());
    assertThat(context.establishmentPv()).isEqualTo(98765);
    assertThat(context.paymentKind()).isEqualTo(ReconciliationMatchContext.PaymentKind.CREDIT);
  }

  @Test
  void releaseContextHasNullEstablishmentFieldsWhenEstablishmentIsNull() {
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setCompany(entityWithId(new CompanyEntity()));
    release.setAcquirer(entityWithId(new AcquirerEntity()));
    release.setModalityPaymentBank(ModalityPaymentBankEnum.CASH_DEBIT);

    ReconciliationMatchContext context = service.contextOf(release);

    assertThat(context.establishmentId()).isNull();
    assertThat(context.establishmentPv()).isNull();
    assertThat(context.paymentKind()).isEqualTo(ReconciliationMatchContext.PaymentKind.DEBIT);
  }

  @Test
  void releaseContextIsNullSafeWhenModalityPaymentBankColumnIsNull() {
    // modality_payment_bank é NULL-ável no banco (dado legado/importação parcial) — sem
    // setModalityPaymentBank(...), getModalityPaymentBank() retorna null (ModalityPaymentBankEnum
    // .fromCode(null) => null). contextOf(ReleasesBankEntity) chamava .getCode() direto sobre
    // esse null e lançava NPE, derrubando reconcilePending() inteiro (única @Transactional).
    ReleasesBankEntity release = new ReleasesBankEntity();
    release.setCompany(entityWithId(new CompanyEntity()));
    release.setAcquirer(entityWithId(new AcquirerEntity()));

    ReconciliationMatchContext context = service.contextOf(release);

    assertThat(context.paymentKind()).isEqualTo(ReconciliationMatchContext.PaymentKind.UNKNOWN);
  }

  private <T extends AuditableEntityBase> T entityWithId(T entity) {
    entity.setId(UUID.randomUUID());
    return entity;
  }
}
