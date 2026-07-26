package com.cardsync.core.file.bank;

import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Backfill único para corrigir a modalidade (débito/crédito) de lançamentos bancários de
 * recebimento (categoria RECEIPT) já importados com {@link ModalityPaymentBankEnum#NULL} —
 * o que os torna invisíveis no Extrato Bancário, já que {@code ReleasesBankSpecs} sempre restringe
 * a listagem a {CASH_DEBIT, CASH_CREDIT, ANTECIP_CRED}, independente de qualquer filtro escolhido
 * na tela.
 *
 * Causa raiz: {@link BankTextSignalResolver#isCreditSignal} não reconhecia "CD" (abreviação real
 * usada pelo Rede/Santander pra crédito, ex.: "REDE   MAST CD0007866470") — mesma classe de bug já
 * corrigida pra bandeira (Cabal/Banescard). Reaproveita o texto já persistido em
 * {@code descriptionHistoricalBank}/{@code documentComplementNumber}/{@code complementRelease} e a
 * MESMA lógica de sinal já corrigida — não é uma reimplementação paralela.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementModalityReclassificationService {

  private final ReleasesBankRepository releasesBankRepository;
  private final BankTextSignalResolver textSignalResolver;

  @Transactional
  public ReclassifyBankStatementModalityResult reclassifyAll() {
    List<ReleasesBankEntity> releases = releasesBankRepository.findUnclassifiedModalityForReclassification(
      ReleaseCategoryEnum.RECEIPT.getCode(),
      ModalityPaymentBankEnum.NULL.getCode()
    );

    int analyzed = 0;
    int stillUnresolved = 0;
    List<ReleasesBankEntity> toSave = new ArrayList<>();

    for (ReleasesBankEntity release : releases) {
      analyzed++;

      String fullText = joinText(
        release.getDescriptionHistoricalBank(),
        release.getDocumentComplementNumber(),
        release.getComplementRelease()
      );
      String normalized = textSignalResolver.normalize(fullText);

      ModalityPaymentBankEnum resolved = resolveModality(normalized);
      if (resolved != null) {
        release.setModalityPaymentBank(resolved);
        toSave.add(release);
      } else {
        stillUnresolved++;
      }
    }

    if (!toSave.isEmpty()) {
      releasesBankRepository.saveAll(toSave);
    }

    log.info(
      "🔄 Reclassificação de modalidade (extrato bancário): analisados={}, atualizados={}, semModalidadeResolvida={}",
      analyzed,
      toSave.size(),
      stillUnresolved
    );

    return new ReclassifyBankStatementModalityResult(analyzed, toSave.size(), stillUnresolved);
  }

  private ModalityPaymentBankEnum resolveModality(String normalizedText) {
    if (textSignalResolver.isDebitSignal(normalizedText)) return ModalityPaymentBankEnum.CASH_DEBIT;
    if (textSignalResolver.isCreditSignal(normalizedText)) return ModalityPaymentBankEnum.CASH_CREDIT;
    return null;
  }

  private String joinText(String... values) {
    StringBuilder result = new StringBuilder();
    for (String value : values) {
      if (value == null || value.isBlank()) continue;
      if (!result.isEmpty()) result.append(' ');
      result.append(value);
    }
    return result.toString();
  }
}
