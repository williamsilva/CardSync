package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.file.config.FileProcessingProperties;
import com.cardsync.domain.model.BankingDomicileEntity;
import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.BankingDomicileRepository;
import com.cardsync.domain.repository.CreditOrderRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Diagnóstico (nunca vincula nada sozinho) para o cenário visto na tela de Conciliação Manual:
 * um lançamento bancário pendente cuja soma de ordens candidatas só fecha o valor quando se
 * ignora o banco de uma ou mais delas — indício de banking_domicile apontando pro banco errado
 * (confirmado com dados reais: RV 86015456, o arquivo EEVD da Rede declarou banco=Santander pra
 * aquela RV, mas o repasse de fato caiu no Sicredi; a empresa já tinha os dois domicílios
 * cadastrados, só essa RV específica saiu resolvida pro banco errado).
 *
 * Reaproveita a mesma janela de data/contexto do matcher automático via
 * {@link BankReconciliationService#isCreditOrderCandidateCompatible}/
 * {@link BankReconciliationService#isCreditOrderCandidateCompatibleIgnoringBank} e o mesmo
 * subset-sum ({@link BankReconciliationMatcher}) — não é uma reimplementação paralela do
 * matching, só uma segunda passada mais permissiva pra achar o que a primeira não acha.
 *
 * Só reporta candidatos pra revisão humana: banco errado é dado de origem (arquivo da
 * adquirente), não algo que o sistema deva corrigir sozinho sem confirmação.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankingDomicileDivergenceService {

  private static final List<Integer> CARD_MODALITY_CODES = List.of(
    ModalityPaymentBankEnum.NULL.getCode(),
    ModalityPaymentBankEnum.CASH_DEBIT.getCode(),
    ModalityPaymentBankEnum.CASH_CREDIT.getCode(),
    ModalityPaymentBankEnum.ANTECIP_CRED.getCode()
  );

  private final ReleasesBankRepository releasesBankRepository;
  private final CreditOrderRepository creditOrderRepository;
  private final BankingDomicileRepository bankingDomicileRepository;
  private final BankReconciliationMatcher matcher;
  private final BankReconciliationService bankReconciliationService;
  private final ReconciliationSettingsService reconciliationSettingsService;
  private final FileProcessingProperties properties;

  @Transactional(readOnly = true)
  public BankingDomicileDivergencePreviewResult preview() {
    List<ReleasesBankEntity> pendingReleases = releasesBankRepository.findPendingForBankingDomicileDivergence(
      StatusPaymentBankEnum.PENDING.getCode(), ReleaseCategoryEnum.RECEIPT.getCode(), CARD_MODALITY_CODES
    );

    CreditOrderMatchSettings settings = CreditOrderMatchSettings.from(reconciliationSettingsService);
    FileProcessingProperties.Reconciliation config = properties.getReconciliation();
    long dpMaxCents = reconciliationSettingsService.getSubsetDpMaxCents();

    List<BankingDomicileDivergenceCandidate> candidates = new ArrayList<>();

    for (ReleasesBankEntity release : pendingReleases) {
      BankingDomicileDivergenceCandidate candidate = analyze(release, settings, config, dpMaxCents);
      if (candidate != null) {
        candidates.add(candidate);
      }
    }

    log.info(
      "🔎 Diagnóstico de domicílio bancário divergente: analisados={}, candidatos={}",
      pendingReleases.size(), candidates.size()
    );

    return new BankingDomicileDivergencePreviewResult(pendingReleases.size(), candidates.size(), candidates);
  }

  private BankingDomicileDivergenceCandidate analyze(
    ReleasesBankEntity release, CreditOrderMatchSettings settings,
    FileProcessingProperties.Reconciliation config, long dpMaxCents
  ) {
    if (release.getCompany() == null || release.getBank() == null
      || release.getReleaseDate() == null || release.getReleaseValue() == null) {
      return null;
    }

    LocalDate windowFrom = release.getReleaseDate().minusDays(settings.toleranceDaysAfter());
    LocalDate windowTo = release.getReleaseDate().plusDays(settings.toleranceDaysBefore());

    List<CreditOrderEntity> candidatesInWindow = creditOrderRepository.findCandidatesForPreImplantationDivergence(
      release.getCompany().getId(), StatusPaymentBankEnum.PENDING.getCode(),
      StatusReconciliationEnum.RECONCILED.getCode(), windowFrom, windowTo
    );

    ReconciliationMatchContext releaseContext = bankReconciliationService.contextOf(release);
    UUID releaseBankId = release.getBank().getId();

    // 1) Respeitando banco normalmente: se já bate, este lançamento não tem problema de domicílio
    //    errado — vai casar sozinho na próxima automática, não é candidato deste diagnóstico.
    List<CreditOrderEntity> sameBank = candidatesInWindow.stream()
      .filter(order -> bankReconciliationService.isCreditOrderCandidateCompatible(
        release, releaseContext, releaseBankId, order, orderMatchDataOf(order),
        settings.toleranceDaysBefore(), settings.toleranceDaysAfter(), settings.strictness()
      ))
      .toList();

    if (matcher.selectByValue(
      sameBank, CreditOrderEntity::getReleaseValue, release.getReleaseValue(), settings.valueTolerance(),
      config.getSafeCapCents(), dpMaxCents
    ).matched()) {
      return null;
    }

    // 2) Ignorando banco: se agora bate, ao menos uma das ordens usadas está com banco diferente
    //    do lançamento — candidata a domicílio errado.
    List<CreditOrderEntity> anyBank = candidatesInWindow.stream()
      .filter(order -> bankReconciliationService.isCreditOrderCandidateCompatibleIgnoringBank(
        release, releaseContext, order, orderMatchDataOf(order),
        settings.toleranceDaysBefore(), settings.toleranceDaysAfter(), settings.strictness()
      ))
      .toList();

    BankReconciliationMatcher.MatchResult expanded = matcher.selectByValue(
      anyBank, CreditOrderEntity::getReleaseValue, release.getReleaseValue(), settings.valueTolerance(),
      config.getSafeCapCents(), dpMaxCents
    );
    if (!expanded.matched()) {
      return null;
    }

    List<CreditOrderEntity> matched = expanded.typedItems();
    List<BankingDomicileMismatchOrder> mismatched = matched.stream()
      .filter(order -> orderBankId(order) == null || !orderBankId(order).equals(releaseBankId))
      .map(order -> toMismatchedOrder(order, release.getCompany().getId(), releaseBankId))
      .toList();

    if (mismatched.isEmpty()) {
      return null;
    }

    return new BankingDomicileDivergenceCandidate(
      release.getId(),
      release.getCompany().getFantasyName(),
      release.getAcquirer() != null ? release.getAcquirer().getFantasyName() : null,
      release.getBank().getName(),
      release.getReleaseDate(), release.getReleaseValue(),
      mismatched
    );
  }

  private BankingDomicileMismatchOrder toMismatchedOrder(CreditOrderEntity order, UUID companyId, UUID releaseBankId) {
    List<BankingDomicileEntity> suggestions = bankingDomicileRepository.findByCompany_IdAndBank_Id(companyId, releaseBankId);
    return new BankingDomicileMismatchOrder(
      order.getId(),
      order.getRvNumber(),
      order.getSalesSummary() != null ? order.getSalesSummary().getId() : null,
      order.getBankingDomicile() != null && order.getBankingDomicile().getBank() != null
        ? order.getBankingDomicile().getBank().getName() : null,
      suggestions.isEmpty() ? null : suggestions.getFirst().getId()
    );
  }

  private UUID orderBankId(CreditOrderEntity order) {
    return order.getBankingDomicile() != null && order.getBankingDomicile().getBank() != null
      ? order.getBankingDomicile().getBank().getId()
      : null;
  }

  private BankReconciliationService.OrderMatchData orderMatchDataOf(CreditOrderEntity order) {
    return new BankReconciliationService.OrderMatchData(bankReconciliationService.contextOf(order), orderBankId(order));
  }
}
