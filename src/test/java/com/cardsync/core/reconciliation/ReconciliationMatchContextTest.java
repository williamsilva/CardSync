package com.cardsync.core.reconciliation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationMatchContextTest {

  private static final ReconciliationMatchContext.MatchStrictness NONE =
    ReconciliationMatchContext.MatchStrictness.NONE;

  private static final UUID COMPANY = UUID.randomUUID();
  private static final UUID ACQUIRER = UUID.randomUUID();
  private static final UUID ESTABLISHMENT = UUID.randomUUID();
  private static final UUID FLAG = UUID.randomUUID();
  private static final UUID OTHER_FLAG = UUID.randomUUID();

  private ReconciliationMatchContext context(
    UUID establishmentId, Integer establishmentPv, UUID flagId, ReconciliationMatchContext.PaymentKind kind
  ) {
    return new ReconciliationMatchContext(COMPANY, ACQUIRER, establishmentId, establishmentPv, flagId, kind);
  }

  // ── Comportamento legado (MatchStrictness.NONE) — contrato de regressão ────

  @Test
  void companyOrAcquirerMismatchIsAlwaysIncompatible() {
    ReconciliationMatchContext base = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext differentCompany = new ReconciliationMatchContext(
      UUID.randomUUID(), ACQUIRER, ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext differentAcquirer = new ReconciliationMatchContext(
      COMPANY, UUID.randomUUID(), ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);

    assertThat(base.compatible(differentCompany, NONE)).isFalse();
    assertThat(base.compatible(differentAcquirer, NONE)).isFalse();
  }

  @Test
  void nullEstablishmentOnEitherSideIsWildcardWhenNotRequired() {
    ReconciliationMatchContext withEstablishment = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext withoutEstablishment = context(null, null, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);

    assertThat(withEstablishment.compatible(withoutEstablishment, NONE)).isTrue();
    assertThat(withoutEstablishment.compatible(withEstablishment, NONE)).isTrue();
  }

  @Test
  void nullOrDifferentFlagIsWildcardWhenNotRequired() {
    ReconciliationMatchContext withFlag = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext withoutFlag = context(ESTABLISHMENT, 100, null, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext differentFlag = context(ESTABLISHMENT, 100, OTHER_FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);

    assertThat(withFlag.compatible(withoutFlag, NONE)).isTrue();
    assertThat(withoutFlag.compatible(withFlag, NONE)).isTrue();
    // sameOptional só é coringa quando um dos lados é nulo — dois valores preenchidos e
    // diferentes continuam incompatíveis mesmo com strictness desligada.
    assertThat(withFlag.compatible(differentFlag, NONE)).isFalse();
  }

  @Test
  void unknownPaymentKindOnEitherSideIsWildcardWhenNotRequired() {
    ReconciliationMatchContext debit = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.DEBIT);
    ReconciliationMatchContext unknown = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.UNKNOWN);

    assertThat(debit.compatible(unknown, NONE)).isTrue();
    assertThat(unknown.compatible(debit, NONE)).isTrue();
  }

  @Test
  void knownPaymentKindMismatchIsIncompatibleEvenWithoutStrictness() {
    ReconciliationMatchContext debit = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.DEBIT);
    ReconciliationMatchContext credit = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);

    assertThat(debit.compatible(credit, NONE)).isFalse();
  }

  // ── Strictness ligada ───────────────────────────────────────────────────────

  @Test
  void establishmentRequiredRejectsNullOnEitherSide() {
    var strictness = new ReconciliationMatchContext.MatchStrictness(false, true, false);
    // Lado do lançamento: establishmentId (UUID real, via EstablishmentEntity) + establishmentPv.
    ReconciliationMatchContext release = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    // Lado da ordem de crédito: contextOf(CreditOrderEntity) nunca popula establishmentId (a
    // entidade não tem essa relação) — só establishmentPv, a partir de pvCentralizer.
    ReconciliationMatchContext orderWithoutPv = context(null, null, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext orderDifferentPv = context(null, 200, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext orderSamePv = context(null, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);

    assertThat(release.compatible(orderWithoutPv, strictness)).isFalse();
    assertThat(release.compatible(orderDifferentPv, strictness)).isFalse();
    // A comparação é pelo número de PV (Integer), não pelo UUID de EstablishmentEntity —
    // CreditOrderEntity não tem essa relação, só pvCentralizer.
    assertThat(release.compatible(orderSamePv, strictness)).isTrue();
  }

  @Test
  void flagRequiredRejectsNullOnEitherSide() {
    var strictness = new ReconciliationMatchContext.MatchStrictness(true, false, false);
    ReconciliationMatchContext withFlag = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext withoutFlag = context(ESTABLISHMENT, 100, null, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext differentFlag = context(ESTABLISHMENT, 100, OTHER_FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);

    assertThat(withFlag.compatible(withoutFlag, strictness)).isFalse();
    assertThat(withFlag.compatible(differentFlag, strictness)).isFalse();
    assertThat(withFlag.compatible(withFlag, strictness)).isTrue();
  }

  @Test
  void paymentKindRequiredRejectsUnknownOnEitherSide() {
    var strictness = new ReconciliationMatchContext.MatchStrictness(false, false, true);
    ReconciliationMatchContext debit = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.DEBIT);
    ReconciliationMatchContext unknown = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.UNKNOWN);
    ReconciliationMatchContext credit = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);

    assertThat(debit.compatible(unknown, strictness)).isFalse();
    assertThat(debit.compatible(credit, strictness)).isFalse();
    assertThat(debit.compatible(debit, strictness)).isTrue();
  }

  @Test
  void allStrictnessFlagsOffReproducesLegacyBehavior() {
    ReconciliationMatchContext a = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext b = context(null, null, null, ReconciliationMatchContext.PaymentKind.UNKNOWN);

    assertThat(a.compatible(b, ReconciliationMatchContext.MatchStrictness.NONE)).isTrue();
    assertThat(a.compatible(b, new ReconciliationMatchContext.MatchStrictness(false, false, false))).isTrue();
  }

  // ── strength() — critério de desempate, não altera compatible() ───────────

  @Test
  void strengthScoresMoreConfirmedFieldsHigher() {
    ReconciliationMatchContext release = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext fullMatch = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);
    ReconciliationMatchContext companyOnlyMatch = context(null, null, null, ReconciliationMatchContext.PaymentKind.UNKNOWN);

    assertThat(release.strength(fullMatch, NONE)).isGreaterThan(release.strength(companyOnlyMatch, NONE));
  }

  @Test
  void strengthIsMinValueWhenIncompatible() {
    ReconciliationMatchContext release = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.DEBIT);
    ReconciliationMatchContext incompatible = context(ESTABLISHMENT, 100, FLAG, ReconciliationMatchContext.PaymentKind.CREDIT);

    assertThat(release.strength(incompatible, NONE)).isEqualTo(Integer.MIN_VALUE);
  }
}
