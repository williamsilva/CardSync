package com.cardsync.core.reconciliation;

import com.cardsync.core.conciliation.ReconciliationSettingsService;
import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Lançamentos pendentes de recebimento por cartão (categoria RECEIPT + modalidade em
 * CASH_DEBIT/CASH_CREDIT/ANTECIP_CRED, mesmo escopo do Extrato Bancário — ver
 * ReleasesBankSpecs#getModalityPaymentBank), escopo compartilhado pelas ferramentas de análise
 * (divergência pré-implantação, legado sem ordem) — PIX/TED/SISPAG e afins nunca terão ordem de
 * crédito candidata, então nem entram na análise. Restrito à janela
 * {@code [go-live, go-live + legacyMarkingMonths]} (ver ImplantationDateProvider/
 * ReconciliationSettingsService#getLegacyMarkingCutoffDate) — lançamentos anteriores ao go-live já
 * são legado por natureza, e lançamentos muito DEPOIS do go-live não podem mais ser explicados por
 * "venda anterior à implantação sem ordem no sistema": esse gap só faz sentido no período de
 * transição logo após o go-live (confirmado com dados reais: um lançamento de 2026 — quase 2 anos
 * após um go-live de 2024 — estava sendo oferecido pra vínculo com essa justificativa, quando na
 * verdade eram vendas correntes com uma ordem qualquer ainda não importada, sem nenhuma relação
 * com implantação).
 */
@Component
@RequiredArgsConstructor
class PendingReceiptReleaseFinder {

  private static final List<Integer> CARD_MODALITY_CODES = List.of(
    ModalityPaymentBankEnum.NULL.getCode(),
    ModalityPaymentBankEnum.CASH_DEBIT.getCode(),
    ModalityPaymentBankEnum.CASH_CREDIT.getCode(),
    ModalityPaymentBankEnum.ANTECIP_CRED.getCode()
  );

  private final ReleasesBankRepository releasesBankRepository;
  private final ImplantationDateProvider implantationDateProvider;
  private final ReconciliationSettingsService reconciliationSettingsService;

  List<ReleasesBankEntity> find() {
    LocalDate legacyMarkingCutoffDate = reconciliationSettingsService.getLegacyMarkingCutoffDate();
    // Nunca manda null pro Postgres: "$param is null or ..." sem coluna do outro lado do "is null"
    // faz o driver falhar com "não foi possível determinar o tipo de dados do parâmetro" (extended
    // query protocol não infere tipo só de "? is null") — confirmado em produção/dev real. Sem
    // teto configurado (go-live nulo), LocalDate.MAX equivale a "sem teto superior" sem precisar
    // de null-check nenhum na query.
    LocalDate upperBound = legacyMarkingCutoffDate != null ? legacyMarkingCutoffDate : LocalDate.MAX;

    return releasesBankRepository.findPendingForPreImplantationDivergence(
      StatusPaymentBankEnum.PENDING.getCode(), ReleaseCategoryEnum.RECEIPT.getCode(),
      CARD_MODALITY_CODES, implantationDateProvider.get(), upperBound
    );
  }
}
