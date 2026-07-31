package com.cardsync.core.reconciliation;

import com.cardsync.domain.model.CreditOrderEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.model.enums.StatusReconciliationEnum;
import com.cardsync.domain.repository.CreditOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Acha as ordens de crédito candidatas de um lançamento bancário, reaproveitando a mesma
 * definição de "candidata compatível" do matcher automático. Tenta primeiro
 * estabelecimento-consciente (ver {@link BankReconciliationService#isCreditOrderCandidateCompatible});
 * só cai para a variante que ignora estabelecimento (ver
 * {@link BankReconciliationService#isCreditOrderCandidateCompatibleIgnoringEstablishment}) quando o
 * PV do próprio lançamento não tem NENHUMA candidata direta — cobrindo o caso de consolidação
 * (algumas empresas têm um único lançamento bancário consolidando valores de mais de um
 * estabelecimento/PV, então exigir o mesmo PV nesse caso descartaria candidatas legítimas).
 *
 * Sem esse fallback em duas etapas, um dia com múltiplos lançamentos do mesmo
 * banco/adquirente/bandeira mas em PVs diferentes (não consolidados) faz o pool ignorando
 * estabelecimento somar candidatas de TODOS esses PVs pra cada lançamento — inflando a soma
 * disponível além do valor de cada lançamento individual e mascarando uma divergência real
 * (falta de ordem) como um falso excesso (SKIPPED_NEGATIVE em
 * PreImplantationDivergenceReconciliationService).
 */
@Component
@RequiredArgsConstructor
class CreditOrderCandidateFinder {

  private final CreditOrderRepository creditOrderRepository;
  private final BankReconciliationService bankReconciliationService;

  List<CreditOrderEntity> findCompatible(ReleasesBankEntity release, CreditOrderMatchSettings settings) {
    if (release.getCompany() == null || release.getReleaseDate() == null || release.getReleaseValue() == null) {
      return List.of();
    }

    ReconciliationMatchContext releaseContext = bankReconciliationService.contextOf(release);
    UUID releaseBankId = release.getBank() != null ? release.getBank().getId() : null;

    // Mesma expressão (e mesma inversão de nomes) de BankReconciliationService#processReleaseForCreditOrderMatch,
    // pra usar exatamente a mesma janela de data do matcher automático.
    LocalDate windowFrom = release.getReleaseDate().minusDays(settings.toleranceDaysAfter());
    LocalDate windowTo = release.getReleaseDate().plusDays(settings.toleranceDaysBefore());

    List<CreditOrderEntity> candidatesInWindow = creditOrderRepository.findCandidatesForPreImplantationDivergence(
      release.getCompany().getId(),
      StatusPaymentBankEnum.PENDING.getCode(),
      StatusReconciliationEnum.RECONCILED.getCode(),
      windowFrom, windowTo
    );

    List<CreditOrderEntity> ordersWithData = candidatesInWindow.stream()
      .filter(order -> order.getReleaseValue() != null && order.getReleaseDate() != null)
      .toList();

    List<CreditOrderEntity> sameEstablishment = ordersWithData.stream()
      .filter(order -> bankReconciliationService.isCreditOrderCandidateCompatible(
        release, releaseContext, releaseBankId, order, orderMatchDataOf(order),
        settings.toleranceDaysBefore(), settings.toleranceDaysAfter(), settings.strictness()
      ))
      .toList();

    if (!sameEstablishment.isEmpty()) {
      return sameEstablishment;
    }

    return ordersWithData.stream()
      .filter(order -> bankReconciliationService.isCreditOrderCandidateCompatibleIgnoringEstablishment(
        release, releaseContext, releaseBankId, order, orderMatchDataOf(order),
        settings.toleranceDaysBefore(), settings.toleranceDaysAfter(), settings.strictness()
      ))
      .toList();
  }

  private BankReconciliationService.OrderMatchData orderMatchDataOf(CreditOrderEntity order) {
    UUID orderBank = order.getBankingDomicile() != null && order.getBankingDomicile().getBank() != null
      ? order.getBankingDomicile().getBank().getId()
      : null;
    return new BankReconciliationService.OrderMatchData(bankReconciliationService.contextOf(order), orderBank);
  }
}
