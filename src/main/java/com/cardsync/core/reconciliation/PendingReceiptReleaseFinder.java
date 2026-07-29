package com.cardsync.core.reconciliation;

import com.cardsync.core.config.ImplantationDateProvider;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lançamentos pendentes de recebimento por cartão (categoria RECEIPT + modalidade em
 * CASH_DEBIT/CASH_CREDIT/ANTECIP_CRED, mesmo escopo do Extrato Bancário — ver
 * ReleasesBankSpecs#getModalityPaymentBank), escopo compartilhado pelas ferramentas de análise
 * (divergência pré-implantação, legado sem ordem) — PIX/TED/SISPAG e afins nunca terão ordem de
 * crédito candidata, então nem entram na análise. Também restrito a
 * {@code releaseDate >= go-live}, mesmo corte usado nas demais listagens do sistema (ver
 * ImplantationDateProvider) — lançamentos anteriores à implantação já são legado por natureza.
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

  List<ReleasesBankEntity> find() {
    return releasesBankRepository.findPendingForPreImplantationDivergence(
      StatusPaymentBankEnum.PENDING.getCode(), ReleaseCategoryEnum.RECEIPT.getCode(),
      CARD_MODALITY_CODES, implantationDateProvider.get()
    );
  }
}
