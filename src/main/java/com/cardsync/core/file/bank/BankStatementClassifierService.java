package com.cardsync.core.file.bank;

import com.cardsync.domain.model.*;
import com.cardsync.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankStatementClassifierService {

  private static final int MODALITY_BANK_DEBIT = 1;
  private static final int MODALITY_BANK_CREDIT = 2;

  private final BankTextSignalResolver textSignalResolver;
  private final BankingDomicileResolver bankingDomicileResolver;
  private final AcquirerRepository acquirerRepository;
  private final EstablishmentRepository establishmentRepository;
  private final FlagRepository flagRepository;

  public BankStatementClassification classify(
    String rawText,
    Integer agency,
    Integer currentAccount,
    CompanyEntity companyFromCnab,
    Cnab240BankLayout layout,
    Integer historicalCode
  ) {
    String normalized = textSignalResolver.normalize(rawText);

    BankStatementClassification classification = new BankStatementClassification();
    classification.setNormalizedText(normalized);
    classification.addPvCandidates(textSignalResolver.extractPvCandidates(rawText));

    Optional<BankingDomicileEntity> domicile = bankingDomicileResolver.resolve(agency, currentAccount, companyFromCnab);
    domicile.ifPresent(classification::setBankingDomicile);

    Optional<EstablishmentEntity> establishment = resolveEstablishment(classification.getPvCandidates())
      .or(() -> domicile.map(BankingDomicileEntity::getEstablishment));
    establishment.ifPresent(classification::setEstablishment);

    CompanyEntity company = companyFromCnab;
    if (company == null && establishment.isPresent()) company = establishment.get().getCompany();
    if (company == null && domicile.isPresent()) company = domicile.get().getCompany();
    classification.setCompany(company);

    resolveAcquirer(normalized).ifPresent(classification::setAcquirer);
    resolveFlag(normalized).ifPresent(classification::setFlag);
    classification.setModalityPaymentBank(resolveBankModality(historicalCode, normalized, layout));

    if (classification.getAcquirer() == null) classification.addNote("acquirer_not_detected_by_text");
    if (classification.getFlag() == null) classification.addNote("flag_not_detected_by_text");
    if (classification.getEstablishment() == null && !classification.getPvCandidates().isEmpty()) {
      classification.addNote("pv_candidates_without_establishment=" + classification.getPvCandidates());
    }

    return classification;
  }

  private Optional<AcquirerEntity> resolveAcquirer(String normalizedText) {
    if (normalizedText == null || normalizedText.isBlank()) return Optional.empty();

    Optional<AcquirerEntity> byKnownAlias = acquirerRepository.findAll().stream()
      .filter(a -> matchesKnownAcquirer(normalizedText, a))
      .findFirst();
    if (byKnownAlias.isPresent()) return byKnownAlias;

    return acquirerRepository.findAll().stream()
      .filter(a -> textSignalResolver.containsNormalized(normalizedText, a.getFantasyName())
        || textSignalResolver.containsNormalized(normalizedText, a.getSocialReason())
        || textSignalResolver.containsNormalized(normalizedText, a.getFileIdentifier()))
      .findFirst();
  }

  private boolean matchesKnownAcquirer(String normalizedText, AcquirerEntity acquirer) {
    String candidate = textSignalResolver.normalize(
      join(acquirer.getFantasyName(), acquirer.getSocialReason(), acquirer.getFileIdentifier())
    );

    if (textSignalResolver.isRedeSignal(normalizedText) && candidate.contains("REDE")) return true;
    if (textSignalResolver.isCieloSignal(normalizedText) && candidate.contains("CIELO")) return true;
    if (textSignalResolver.isStoneSignal(normalizedText) && candidate.contains("STONE")) return true;
    if (textSignalResolver.isGetnetSignal(normalizedText) && candidate.contains("GETNET")) return true;

    return false;
  }

  private Optional<FlagEntity> resolveFlag(String normalizedText) {
    if (normalizedText == null || normalizedText.isBlank()) return Optional.empty();

    Optional<FlagEntity> byKnownAlias = flagRepository.findAll().stream()
      .filter(f -> matchesKnownFlag(normalizedText, f))
      .findFirst();
    if (byKnownAlias.isPresent()) return byKnownAlias;

    return flagRepository.findAll().stream()
      .filter(f -> textSignalResolver.containsNormalized(normalizedText, f.getName())
        || textSignalResolver.containsNormalized(normalizedText, String.valueOf(f.getErpCode())))
      .findFirst();
  }

  private boolean matchesKnownFlag(String normalizedText, FlagEntity flag) {
    String name = textSignalResolver.normalize(flag.getName());
    if (textSignalResolver.isVisaSignal(normalizedText) && name.contains("VISA")) return true;
    if (textSignalResolver.isMasterSignal(normalizedText) && (name.contains("MASTER") || name.contains("MASTERCARD"))) return true;
    if (textSignalResolver.isEloSignal(normalizedText) && name.contains("ELO")) return true;
    if (textSignalResolver.isAmexSignal(normalizedText) && (name.contains("AMEX") || name.contains("AMERICAN"))) return true;
    return false;
  }

  private Optional<EstablishmentEntity> resolveEstablishment(List<Integer> pvCandidates) {
    if (pvCandidates == null || pvCandidates.isEmpty()) return Optional.empty();

    for (Integer pv : pvCandidates) {
      Optional<EstablishmentEntity> found = establishmentRepository.findAll().stream()
        .filter(e -> e.getPvNumber() != null && e.getPvNumber().equals(pv))
        .findFirst();
      if (found.isPresent()) return found;
    }

    return Optional.empty();
  }

  private Integer resolveBankModality(Integer historicalCode, String normalizedText, Cnab240BankLayout layout) {
    if (layout != null && !layout.isUsesDescriptionForModality()) {
      return historicalCode;
    }
    if (textSignalResolver.isDebitSignal(normalizedText)) return MODALITY_BANK_DEBIT;
    if (textSignalResolver.isCreditSignal(normalizedText)) return MODALITY_BANK_CREDIT;
    return historicalCode;
  }

  private String join(String... values) {
    StringBuilder sb = new StringBuilder();
    if (values == null) return "";
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(value);
    }
    return sb.toString();
  }
}
