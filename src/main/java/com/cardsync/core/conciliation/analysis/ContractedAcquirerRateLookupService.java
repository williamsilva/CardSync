package com.cardsync.core.conciliation.analysis;

import com.cardsync.domain.model.ContractEntity;
import com.cardsync.domain.model.ContractRateEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.model.enums.CaptureEnum;
import com.cardsync.domain.model.enums.ContractEnum;
import com.cardsync.domain.model.enums.ModalityEnum;
import com.cardsync.domain.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractedAcquirerRateLookupService {

  private final ContractRepository contractRepository;

  @Transactional(readOnly = true)
  public Optional<ContractedAcquirerRate> findRate(TransactionAcqEntity tx) {
    if (tx == null || tx.getAcquirer() == null || tx.getFlag() == null || tx.getModality() == null || tx.getSaleDate() == null) {
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

  private Optional<ContractedAcquirerRate> buildRate(ContractEntity contract, TransactionAcqEntity tx) {
    return contract.getContractFlags().stream()
      .filter(contractFlag -> contractFlag.getFlag() != null)
      .filter(contractFlag -> Objects.equals(contractFlag.getFlag().getId(), tx.getFlag().getId()))
      .flatMap(contractFlag -> contractFlag.getContractRates().stream())
      .filter(rate -> matchesModality(rate, tx.getModality()))
      .filter(rate -> matchesInstallmentCount(rate, tx.getInstallment()))
      .max(Comparator.comparingInt(this::specificity))
      .map(rate -> toContractedRate(contract, rate, tx));
  }

  private boolean matchesModality(ContractRateEntity rate, Integer modality) {
    ModalityEnum rateModality = rate.getModality();
    return rateModality != null && Objects.equals(rateModality.getCode(), modality);
  }

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

  private ContractedAcquirerRate toContractedRate(ContractEntity contract, ContractRateEntity contractRate, TransactionAcqEntity tx) {
    boolean ecommerce = Objects.equals(tx.getCapture(), CaptureEnum.ECOMMERCE.getCode());
    BigDecimal rate = ecommerce && contractRate.getRateEcommerce() != null
      ? contractRate.getRateEcommerce()
      : contractRate.getRate();
    Integer paymentTermDays = ecommerce && contractRate.getPaymentTermDaysEcommerce() != null
      ? contractRate.getPaymentTermDaysEcommerce()
      : contractRate.getPaymentTermDays();

    return new ContractedAcquirerRate(contract, contractRate, nvl(rate), nvl(paymentTermDays), ecommerce);
  }

  private BigDecimal nvl(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private Integer nvl(Integer value) {
    return value == null ? 0 : value;
  }
}
