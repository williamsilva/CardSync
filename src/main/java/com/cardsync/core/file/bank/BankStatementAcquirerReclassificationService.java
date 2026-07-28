package com.cardsync.core.file.bank;

import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Backfill único para vincular o adquirente de lançamentos bancários de recebimento já
 * importados sem adquirente resolvido. O adquirente é parte do contexto de casamento usado na
 * conciliação automática (ver BankReconciliationService#contextOf) — sem ele, um lançamento pode
 * nunca casar mesmo tendo ordem de crédito compatível em todo o resto.
 *
 * Reaproveita o texto já persistido em {@code descriptionHistoricalBank}/
 * {@code documentComplementNumber}/{@code complementRelease} e a MESMA lógica de resolução já
 * usada na importação original ({@link BankStatementClassifierService#resolveAcquirer}) — não é
 * uma reimplementação paralela.
 *
 * Rode ANTES de "Reclassificar Estabelecimento": {@code resolveEstablishment} usa o adquirente já
 * vinculado do lançamento para restringir a busca por PV, então corrigir o adquirente primeiro
 * melhora a taxa de acerto do estabelecimento também.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementAcquirerReclassificationService {

  private final ReleasesBankRepository releasesBankRepository;
  private final BankStatementClassifierService bankStatementClassifierService;
  private final BankTextSignalResolver textSignalResolver;
  private final AcquirerRepository acquirerRepository;

  @Transactional
  public ReclassifyBankStatementAcquirerResult reclassifyAll() {
    List<ReleasesBankEntity> releases = releasesBankRepository
      .findWithoutAcquirerForReclassification(ReleaseCategoryEnum.RECEIPT.getCode());

    // Carregada uma única vez: resolveAcquirer(text, acquirers) evita repetir
    // acquirerRepository.findAll() a cada lançamento — mesmo ajuste já feito em resolveFlag.
    List<AcquirerEntity> allAcquirers = acquirerRepository.findAll();

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
      Optional<AcquirerEntity> resolved = bankStatementClassifierService.resolveAcquirer(normalized, allAcquirers);

      if (resolved.isPresent()) {
        release.setAcquirer(resolved.get());
        toSave.add(release);
      } else {
        stillUnresolved++;
      }
    }

    if (!toSave.isEmpty()) {
      releasesBankRepository.saveAll(toSave);
    }

    log.info(
      "🔄 Reclassificação de adquirente (extrato bancário): analisados={}, atualizados={}, semAdquirenteResolvido={}",
      analyzed,
      toSave.size(),
      stillUnresolved
    );

    return new ReclassifyBankStatementAcquirerResult(analyzed, toSave.size(), stillUnresolved);
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
