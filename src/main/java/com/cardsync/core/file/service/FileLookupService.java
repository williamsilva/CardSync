package com.cardsync.core.file.service;

import com.cardsync.domain.model.*;
import com.cardsync.domain.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileLookupService {
  private final AcquirerRepository acquirerRepository;
  private final OriginFileRepository originFileRepository;
  private final FlagRepository flagRepository;
  private final EstablishmentRepository establishmentRepository;
  private final RelationFlagAcquirerRepository relationFlagAcquirerRepository;

  @Transactional(readOnly = true)
  public OriginFileEntity origin(String code) {
    return originFileRepository.findByCodeIgnoreCase(code)
      .orElseThrow(() -> new IllegalStateException("Origem de arquivo não cadastrada: " + code));
  }

  @Transactional(readOnly = true)
  public AcquirerEntity acquirerByIdentifier(String identifier) {
    for (String candidate : acquirerCandidates(identifier)) {
      Optional<AcquirerEntity> found = acquirerRepository.findByFileIdentifierIgnoreCase(candidate)
        .or(() -> acquirerRepository.findByFantasyNameIgnoreCase(candidate));
      if (found.isPresent()) return found.get();
    }
    throw new IllegalStateException("Adquirente não cadastrada para identificador: " + identifier);
  }

  @Transactional(readOnly = true)
  public EstablishmentEntity establishmentByPvNumber(Integer pvNumber) {
    return establishmentRepository.lookupByPvNumber(pvNumber, PageRequest.of(0, 1))
      .stream()
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Estabelecimento não cadastrado para PV: " + pvNumber));
  }

  @Transactional(readOnly = true)
  public FlagEntity flagByName(String name) {
    for (String candidate : flagCandidates(name)) {
      Optional<FlagEntity> found = flagRepository.findByNameIgnoreCase(candidate);
      if (found.isPresent()) return found.get();
    }
    throw new IllegalStateException("Bandeira não cadastrada para nome: " + name);
  }

  @Transactional(readOnly = true)
  public FlagEntity flagByAcquirerCode(AcquirerEntity acquirer, String acquirerCode) {
    return relationFlagAcquirerRepository.findByAcquirer_IdAndAcquirerCode(acquirer.getId(), acquirerCode)
      .map(RelationFlagAcquirerEntity::getFlag)
      .orElseThrow(() -> new IllegalStateException("Bandeira não cadastrada para adquirente " + acquirer.getFantasyName() + " e código " + acquirerCode));
  }

  private Set<String> acquirerCandidates(String identifier) {
    Set<String> candidates = new LinkedHashSet<>();
    if (identifier == null || identifier.isBlank()) return candidates;

    String original = identifier.trim();
    String normalized = normalize(original);
    candidates.add(original);

    if (normalized.contains("redecard") || normalized.equals("rede") || normalized.contains(" rede ") || normalized.startsWith("rede ")) {
      candidates.add("Rede");
      candidates.add("Rede S/A");
    }
    if (normalized.contains("cielo") || normalized.contains("visanet")) {
      candidates.add("Cielo");
      candidates.add("Cielo S/A");
    }
    if (normalized.contains("safra")) candidates.add("SafraPay");

    return candidates;
  }

  private Set<String> flagCandidates(String name) {
    Set<String> candidates = new LinkedHashSet<>();
    if (name == null || name.isBlank()) return candidates;

    String original = name.trim();
    String normalized = normalize(original);
    candidates.add(original);

    if (containsAny(normalized, "mastercard", "master card", "master", "maestro")) candidates.add("Mastercard");
    if (containsAny(normalized, "visa", "electron")) candidates.add("Visa");
    if (containsAny(normalized, "amex", "american express")) candidates.add("American Express");
    if (normalized.contains("diners")) candidates.add("Diners Club");
    if (normalized.contains("hiper")) candidates.add("Hipercard");
    if (normalized.contains("elo")) candidates.add("Elo");
    if (normalized.contains("picpay")) candidates.add("PicPay");
    if (normalized.contains("jcb")) candidates.add("JCB");
    if (normalized.contains("banescard")) candidates.add("Banescard");
    if (normalized.contains("cabal")) candidates.add("Cabal");
    if (normalized.contains("sorocred")) candidates.add("Sorocred");
    if (normalized.equals("cup") || normalized.contains("china union")) candidates.add("CUP");
    if (normalized.contains("sicredi")) candidates.add("Sicredi");
    if (normalized.contains("avista")) candidates.add("Avista");
    if (normalized.contains("credz")) candidates.add("Credz");
    if (normalized.contains("banricompras")) candidates.add("Banricompras");
    if (normalized.contains("pix")) candidates.add("Pix");

    candidates.add("Outras");
    return candidates;
  }

  private boolean containsAny(String text, String... values) {
    for (String value : values) {
      if (text.contains(value)) return true;
    }
    return false;
  }

  private String normalize(String value) {
    return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
      .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
      .replaceAll("[^a-zA-Z0-9]+", " ")
      .replaceAll("\\s+", " ")
      .trim()
      .toLowerCase(Locale.ROOT);
  }
}
