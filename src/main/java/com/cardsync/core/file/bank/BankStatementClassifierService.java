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

  private final FlagRepository flagRepository;
  private final AcquirerRepository acquirerRepository;
  private final BankTextSignalResolver textSignalResolver;
  private final BankingDomicileResolver bankingDomicileResolver;
  private final EstablishmentRepository establishmentRepository;

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

    Optional<BankingDomicileEntity> domicile = bankingDomicileResolver.resolve(layout != null ? layout.getBankCode() : null, agency, currentAccount, companyFromCnab);
    domicile.ifPresent(classification::setBankingDomicile);

    CompanyEntity company = companyFromCnab;
    if (company == null && domicile.isPresent()) company = domicile.get().getCompany();
    classification.setCompany(company);

    resolveAcquirer(normalized).ifPresent(classification::setAcquirer);
    resolveFlag(normalized).ifPresent(classification::setFlag);
    classification.setModalityPaymentBank(resolveBankModality(historicalCode, normalized, layout));
    resolveEstablishment(classification.getPvCandidates(), classification.getAcquirer())
      .ifPresent(classification::setEstablishment);

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

  /** Visibilidade de pacote (não private) para permitir teste unitário direto sem contexto Spring. */
  Optional<FlagEntity> resolveFlag(String normalizedText) {
    return resolveFlag(normalizedText, flagRepository.findAll());
  }

  /**
   * Mesma resolução, mas recebendo a lista de bandeiras já carregada — usado por
   * BankStatementFlagReclassificationService para reclassificar em lote sem repetir
   * flagRepository.findAll() a cada lançamento (antes eram 2 consultas extras por linha).
   */
  Optional<FlagEntity> resolveFlag(String normalizedText, List<FlagEntity> flags) {
    if (normalizedText == null || normalizedText.isBlank()) return Optional.empty();

    Optional<FlagEntity> byKnownAlias = flags.stream()
      .filter(f -> matchesKnownFlag(normalizedText, f))
      .findFirst();
    if (byKnownAlias.isPresent()) return byKnownAlias;

    // Antes também casava por erp_code (String.valueOf(f.getErpCode())) como substring solta no
    // texto — um código de 1-2 dígitos (ex.: American Express=3) quase sempre aparece por
    // coincidência dentro do PV/referência do lançamento (ex.: "867379"), então bandeiras sem
    // sinal próprio em matchesKnownFlag (Cabal, Hipercard, ...) nunca chegavam a ser avaliadas
    // por nome — o primeiro erp_code coincidente na ordem de findAll() vencia antes. Casar só
    // pelo nome é mais restrito, mas correto para bandeiras cujo texto do banco traz o nome por
    // extenso (ex.: "867379REDE-CABAL DEB" contém "CABAL"); bandeiras abreviadas pelo banco
    // (ex.: Banescard -> "BANESC") continuam precisando de sinal próprio em matchesKnownFlag.
    return flags.stream()
      .filter(f -> textSignalResolver.containsNormalized(normalizedText, f.getName()))
      .findFirst();
  }

  private boolean matchesKnownFlag(String normalizedText, FlagEntity flag) {
    String name = textSignalResolver.normalize(flag.getName());
    if (textSignalResolver.isVisaSignal(normalizedText) && name.contains("VISA")) return true;
    if (textSignalResolver.isMasterSignal(normalizedText) && (name.contains("MASTER") || name.contains("MASTERCARD"))) return true;
    if (textSignalResolver.isEloSignal(normalizedText) && name.contains("ELO")) return true;
    if (textSignalResolver.isAmexSignal(normalizedText) && (name.contains("AMEX") || name.contains("AMERICAN"))) return true;
    // Santander abrevia "Banescard" para "BANESC" no histórico — o nome completo nunca aparece,
    // então o casamento por substring de nome (Pass 2 de resolveFlag) nunca bate sozinho.
    if (textSignalResolver.isBanescardSignal(normalizedText) && name.contains("BANESCARD")) return true;
    return false;
  }

  private Optional<EstablishmentEntity> resolveEstablishment(List<Integer> pvCandidates, AcquirerEntity acquirer) {
    if (pvCandidates == null || pvCandidates.isEmpty()) return Optional.empty();

    for (Integer pv : pvCandidates) {
      Optional<EstablishmentEntity> found = acquirer != null
        ? establishmentRepository.findFirstByPvNumberAndAcquirer_Id(pv, acquirer.getId())
        : establishmentRepository.findFirstByPvNumber(pv);
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
