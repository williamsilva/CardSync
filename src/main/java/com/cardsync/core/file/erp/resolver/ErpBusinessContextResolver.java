package com.cardsync.core.file.erp.resolver;

import com.cardsync.core.file.erp.dto.TransactionErpCsvDto;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.CompanyEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.TransactionErpEntity;
import com.cardsync.domain.repository.CompanyRepository;
import com.cardsync.domain.repository.EstablishmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErpBusinessContextResolver {

  private final CompanyRepository companyRepository;
  private final EstablishmentRepository establishmentRepository;

  @Transactional(readOnly = true)
  public void resolve(TransactionErpCsvDto row, TransactionErpEntity tx) {
    tx.setSourceCompanyCnpj(row.getCompanyCnpj());
    tx.setSourceCompanyName(row.getCompanyName());
    tx.setSourceEstablishmentPvNumber(row.getEstablishmentPvNumber());
    tx.setSourceEstablishmentName(row.getEstablishmentName());
    resolve(tx);
  }

  /**
   * Reexecuta a resolução comercial usando os dados brutos salvos na transação ERP.
   * Esse método é usado no processamento e no reprocessamento de pendências.
   */
  @Transactional(readOnly = true)
  public void resolve(TransactionErpEntity tx) {
    AcquirerEntity acquirer = tx.getAcquirer();
    String companyCnpj = normalizeCnpj(tx.getSourceCompanyCnpj());
    Integer pvNumber = tx.getSourceEstablishmentPvNumber();

    if (pvNumber == null) {
      pvNumber = extractPvNumber(tx.getSourceEstablishmentName());
      if (pvNumber != null) {
        tx.setSourceEstablishmentPvNumber(pvNumber);
      }
    }

    // 1) Empresa primeiro: no Nimbus, o contexto comercial orienta a busca do PV/contrato.
    resolveCompany(companyCnpj, tx.getSourceCompanyName()).ifPresent(tx::setCompany);

    // 2) Estabelecimento por PV, priorizando CNPJ + adquirente.
    Optional<EstablishmentEntity> establishment = resolveEstablishment(companyCnpj, pvNumber, acquirer);
    establishment.ifPresent(tx::setEstablishment);

    // 3) Se achou estabelecimento, a empresa do estabelecimento é a fonte mais confiável.
    if (tx.getEstablishment() != null && tx.getEstablishment().getCompany() != null) {
      tx.setCompany(tx.getEstablishment().getCompany());
      return;
    }

    // 4) Fallback controlado: quando o arquivo não traz PV, mas empresa + adquirente
    // apontam para um único estabelecimento, resolvemos automaticamente.
    if (tx.getEstablishment() == null && tx.getCompany() != null && acquirer != null) {
      List<EstablishmentEntity> candidates = establishmentRepository.findByCompany_IdAndAcquirer_IdOrderByPvNumberAsc(
        tx.getCompany().getId(),
        acquirer.getId()
      );

      if (candidates.size() == 1) {
        tx.setEstablishment(candidates.getFirst());
        log.info("✅ ERP PV resolvido por fallback empresa+adquirente únicos. nsu={}, company={}, acquirer={}, pv={}",
          tx.getNsu(),
          tx.getCompany().getId(),
          acquirer.getId(),
          tx.getEstablishment().getPvNumber()
        );
        return;
      }

      if (candidates.size() > 1) {
        log.debug("ERP sem PV e com múltiplos estabelecimentos possíveis. nsu={}, company={}, acquirer={}, candidatos={}",
          tx.getNsu(), tx.getCompany().getId(), acquirer.getId(), candidates.size());
      }
    }

    if (tx.getCompany() == null || tx.getEstablishment() == null) {
      log.debug(
        "Contexto ERP incompleto. nsu={}, cnpjEmpresa={}, empresa='{}', pv={}, estabelecimento='{}', adquirente='{}'. companyResolved={}, establishmentResolved={}",
        tx.getNsu(),
        companyCnpj,
        tx.getSourceCompanyName(),
        pvNumber,
        tx.getSourceEstablishmentName(),
        acquirer != null ? acquirer.getFantasyName() : null,
        tx.getCompany() != null,
        tx.getEstablishment() != null
      );
    }
  }

  private Optional<EstablishmentEntity> resolveEstablishment(String companyCnpj, Integer pvNumber, AcquirerEntity acquirer) {
    if (pvNumber == null) return Optional.empty();

    if (companyCnpj != null && acquirer != null) {
      Optional<EstablishmentEntity> exact = first(establishmentRepository
        .lookupByPvNumberAndCompanyCnpjAndAcquirerId(pvNumber, companyCnpj, acquirer.getId(), PageRequest.of(0, 1)));
      if (exact.isPresent()) return exact;
    }

    if (acquirer != null) {
      Optional<EstablishmentEntity> byAcquirer = first(establishmentRepository
        .lookupByPvNumberAndAcquirerId(pvNumber, acquirer.getId(), PageRequest.of(0, 1)));
      if (byAcquirer.isPresent()) return byAcquirer;
    }

    if (companyCnpj != null) {
      Optional<EstablishmentEntity> byCompany = first(establishmentRepository
        .lookupByPvNumberAndCompanyCnpj(pvNumber, companyCnpj, PageRequest.of(0, 1)));
      if (byCompany.isPresent()) return byCompany;
    }

    return first(establishmentRepository.lookupByPvNumber(pvNumber, PageRequest.of(0, 1)));
  }

  private Optional<EstablishmentEntity> first(List<EstablishmentEntity> establishments) {
    if (establishments == null || establishments.isEmpty()) return Optional.empty();
    return Optional.of(establishments.getFirst());
  }

  private Optional<CompanyEntity> resolveCompany(String companyCnpj, String companyName) {
    if (companyCnpj != null) {
      Optional<CompanyEntity> byCnpj = companyRepository.findByCnpj(companyCnpj);
      if (byCnpj.isPresent()) return byCnpj;
    }
    if (companyName != null && !companyName.isBlank()) {
      String normalizedName = companyName.trim();
      Optional<CompanyEntity> byFantasyName = companyRepository.findByFantasyNameIgnoreCase(normalizedName);
      if (byFantasyName.isPresent()) return byFantasyName;

      Optional<CompanyEntity> bySocialReason = companyRepository.findBySocialReasonIgnoreCase(normalizedName);
      if (bySocialReason.isPresent()) return bySocialReason;

      List<CompanyEntity> candidates = companyRepository.lookupByName(normalizedName, PageRequest.of(0, 2));
      if (candidates.size() == 1) return Optional.of(candidates.getFirst());
      if (candidates.size() > 1) {
        log.debug("ERP empresa por nome ambígua. nome='{}', candidatos={}", normalizedName, candidates.size());
      }
    }
    return Optional.empty();
  }

  private Integer extractPvNumber(String value) {
    if (value == null || value.isBlank()) return null;
    String digits = value.replaceAll("[^0-9]", "");
    if (digits.isBlank()) return null;
    try {
      return Integer.valueOf(digits);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String normalizeCnpj(String value) {
    if (value == null || value.isBlank()) return null;
    String digits = value.replaceAll("[^0-9]", "");
    return digits.isBlank() ? null : digits;
  }
}
