package com.cardsync.core.file.erp.contract;

import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.ContractRateEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ContractEnum;
import com.cardsync.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractedErpRateLookupService {

  private final ContractRepository contractRepository;

  @Transactional(readOnly = true)
  public Optional<ContractedErpRate> findRate(TransactionErpEntity tx) {
    if (tx.getAcquirer() == null || tx.getFlag() == null || tx.getModality() == null || tx.getSaleDate() == null) {
      return Optional.empty();
    }

    UUID companyId = tx.getCompany() == null ? null : tx.getCompany().getId();
    UUID establishmentId = tx.getEstablishment() == null ? null : tx.getEstablishment().getId();
    LocalDate saleDate = tx.getSaleDate().toLocalDate();
    Set<UUID> seenContracts = new HashSet<>();

    return contractRepository.findCandidatesForErpRate(
        companyId,
        tx.getAcquirer().getId(),
        establishmentId,
        tx.getFlag().getId(),
        tx.getModality(),
        ContractEnum.VALIDITY.getCode(),
        saleDate
      )
      .stream()
      .filter(contract -> contract != null && contract.getId() != null && seenContracts.add(contract.getId()))
      .map(contract -> buildRate(contract, tx))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .findFirst();
  }

  private Optional<ContractedErpRate> buildRate(ContractEntity contract, TransactionErpEntity tx) {
    return contract.getContractFlags().stream()
      .filter(contractFlag -> contractFlag.getFlag() != null)
      .filter(contractFlag -> Objects.equals(contractFlag.getFlag().getId(), tx.getFlag().getId()))
      .flatMap(contractFlag -> contractFlag.getContractRates().stream())
      .filter(rate -> Objects.equals(rate.getModality().getCode(), tx.getModality()))
      .filter(rate -> matchesInstallmentCount(rate, tx.getInstallment()))
      .sorted((left, right) -> Integer.compare(specificity(right), specificity(left)))
      .findFirst()
      .map(rate -> toContractedRate(contract, rate, tx));
  }

  /**
   * Considera a quantidade de parcelas na busca da taxa contratada.
   *
   * Campos novos opcionais:
   * - installmentMin: primeira parcela/faixa atendida pela taxa;
   * - installmentMax: última parcela/faixa atendida pela taxa.
   *
   * Compatibilidade:
   * - taxas antigas sem faixa continuam válidas para qualquer quantidade;
   * - se existir taxa por faixa, ganha a faixa mais específica compatível.
   */
  private boolean matchesInstallmentCount(ContractRateEntity rate, Integer installmentCount) {
    int count = installmentCount == null || installmentCount <= 0 ? 1 : installmentCount;
    Integer min = rate.getInstallmentMin();
    Integer max = rate.getInstallmentMax();

    if (min == null && max == null) return true;
    if (min != null && count < min) return false;
    return max == null || count <= max;
  }

  private int specificity(ContractRateEntity rate) {
    Integer min = rate.getInstallmentMin();
    Integer max = rate.getInstallmentMax();
    if (min == null && max == null) return 0;
    if (min != null && max != null) return Math.max(1, 1000 - Math.abs(max - min));
    return 1;
  }

  private ContractedErpRate toContractedRate(ContractEntity contract, ContractRateEntity contractRate, TransactionErpEntity tx) {
    boolean ecommerce = Objects.equals(tx.getCapture(), CaptureEnum.ECOMMERCE.getCode());
    BigDecimal rate = ecommerce && contractRate.getRateEcommerce() != null
      ? contractRate.getRateEcommerce()
      : contractRate.getRate();
    Integer paymentTermDays = ecommerce && contractRate.getPaymentTermDaysEcommerce() != null
      ? contractRate.getPaymentTermDaysEcommerce()
      : contractRate.getPaymentTermDays();

    return new ContractedErpRate(contract, contractRate, nvl(rate), nvl(paymentTermDays), ecommerce);
  }

  private BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private Integer nvl(Integer value) {
    return value == null ? 0 : value;
  }
}
