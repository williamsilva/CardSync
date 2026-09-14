package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.representation.input.ContractRateInput;
import com.cardsync.domain.model.ContractFlagEntity;
import com.cardsync.domain.model.ContractRateEntity;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.ContractRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import com.cardsync.domain.repository.FlagRepository;
import com.nimbussystems.commons.legacy.exceptionhandler.BusinessException;
import com.cardsync.infrastructure.repository.spec.ContractSpecs;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Achado real (auditoria 2026-09-13): payment_term_days/payment_term_days_ecommerce são NOT NULL
 * no banco, mas nada impedia gravar 0 - nenhum contrato real hoje tem prazo 0 (mínimo cadastrado
 * é D+1), então 0 é sempre um campo esquecido no formulário, nunca um prazo de verdade. Sem essa
 * guarda, InstallmentErpGenerator vencia a parcela no próprio dia da venda, silenciosamente.
 *
 * Achado relacionado: rate_ecommerce/payment_term_days_ecommerce, por serem NOT NULL, nunca
 * podiam ficar null pra acionar o fallback "sem valor específico de e-commerce, usa o
 * presencial" já escrito em Contracted{Erp,Acquirer}RateLookupService - o default anterior
 * (ZERO/0) tornava esse fallback morto: toda venda e-commerce de um contrato sem taxa/prazo
 * e-commerce explícitos saía com MDR 0% e vencimento no dia da venda.
 */
class ContractServiceTest {

  private final ContractService service = new ContractService(
    mock(ContractSpecs.class),
    mock(FlagRepository.class),
    mock(CompanyRepository.class),
    mock(ContractRepository.class),
    mock(AcquirerRepository.class),
    mock(EstablishmentRepository.class)
  );

  @Test
  void rejectsZeroPaymentTermDays() {
    UUID flagId = UUID.randomUUID();
    List<ContractRateInput> rates = List.of(rateInput(ModalityEnum.CASH_CREDIT, "1.50", 0, null, null));

    assertThatThrownBy(() -> service.validateRates(flagId, rates))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining("paymentTermDays");
  }

  @Test
  void rejectsZeroPaymentTermDaysEcommerceWhenExplicitlyInformed() {
    UUID flagId = UUID.randomUUID();
    List<ContractRateInput> rates = List.of(rateInput(ModalityEnum.CASH_CREDIT, "1.50", 1, null, 0));

    assertThatThrownBy(() -> service.validateRates(flagId, rates))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining("paymentTermDaysEcommerce");
  }

  @Test
  void acceptsPositivePaymentTermDaysWithNoEcommerceOverride() {
    UUID flagId = UUID.randomUUID();
    List<ContractRateInput> rates = List.of(rateInput(ModalityEnum.CASH_CREDIT, "1.50", 31, null, null));

    assertThatCode(() -> service.validateRates(flagId, rates)).doesNotThrowAnyException();
  }

  @Test
  void syncRatesDefaultsEcommerceFieldsToTheSameValueAsThePresentialOnesWhenNotInformed() {
    ContractFlagEntity contractFlag = new ContractFlagEntity();
    List<ContractRateInput> rates = List.of(rateInput(ModalityEnum.CASH_CREDIT, "2.34", 31, null, null));

    service.syncRates(contractFlag, rates);

    ContractRateEntity saved = contractFlag.getContractRates().get(0);
    assertThat(saved.getRateEcommerce()).isEqualByComparingTo("2.34");
    assertThat(saved.getPaymentTermDaysEcommerce()).isEqualTo(31);
  }

  @Test
  void syncRatesPreservesExplicitEcommerceValuesWhenInformed() {
    ContractFlagEntity contractFlag = new ContractFlagEntity();
    List<ContractRateInput> rates = List.of(rateInput(ModalityEnum.CASH_CREDIT, "2.34", 31, new BigDecimal("3.10"), 34));

    service.syncRates(contractFlag, rates);

    ContractRateEntity saved = contractFlag.getContractRates().get(0);
    assertThat(saved.getRateEcommerce()).isEqualByComparingTo("3.10");
    assertThat(saved.getPaymentTermDaysEcommerce()).isEqualTo(34);
  }

  private ContractRateInput rateInput(
    ModalityEnum modality, String rate, Integer paymentTermDays, BigDecimal rateEcommerce, Integer paymentTermDaysEcommerce
  ) {
    return new ContractRateInput(modality, new BigDecimal(rate), paymentTermDays, rateEcommerce, paymentTermDaysEcommerce);
  }
}
