package com.cardsync.core.file.bank;

import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Backfill único para vincular o estabelecimento de lançamentos bancários de recebimento já
 * importados sem estabelecimento resolvido — causado por
 * {@link BankTextSignalResolver#extractPvCandidates} não reconhecer o PV quando colado direto
 * (sem separador) a um marcador de texto, algo que varia por banco (ex.: Itaú/Rede: "REDE   MAST
 * CD0007866470"; Santander/Getnet: "350834GETNET-VISA") — ver correção do NUMBER_PATTERN.
 *
 * Reaproveita o texto já persistido em {@code descriptionHistoricalBank}/
 * {@code documentComplementNumber}/{@code complementRelease} e a MESMA lógica de resolução já
 * corrigida ({@link BankStatementClassifierService#resolveEstablishment}) — não é uma
 * reimplementação paralela.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementEstablishmentReclassificationService {

  private final ReleasesBankRepository releasesBankRepository;
  private final BankStatementClassifierService bankStatementClassifierService;
  private final BankTextSignalResolver textSignalResolver;

  @Transactional
  public ReclassifyBankStatementEstablishmentResult reclassifyAll() {
    List<ReleasesBankEntity> releases = releasesBankRepository
      .findWithoutEstablishmentForReclassification(ReleaseCategoryEnum.RECEIPT.getCode());

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

      List<Integer> pvCandidates = textSignalResolver.extractPvCandidates(fullText);
      Optional<EstablishmentEntity> resolved = bankStatementClassifierService
        .resolveEstablishment(pvCandidates, release.getAcquirer());

      if (resolved.isPresent()) {
        release.setEstablishment(resolved.get());
        toSave.add(release);
      } else {
        stillUnresolved++;
      }
    }

    if (!toSave.isEmpty()) {
      releasesBankRepository.saveAll(toSave);
    }

    log.info(
      "🔄 Reclassificação de estabelecimento (extrato bancário): analisados={}, atualizados={}, semEstabelecimentoResolvido={}",
      analyzed,
      toSave.size(),
      stillUnresolved
    );

    return new ReclassifyBankStatementEstablishmentResult(analyzed, toSave.size(), stillUnresolved);
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
