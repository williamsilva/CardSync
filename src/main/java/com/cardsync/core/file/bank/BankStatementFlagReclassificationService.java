package com.cardsync.core.file.bank;

import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.ReleasesBankEntity;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import com.cardsync.domain.repository.FlagRepository;
import com.cardsync.domain.repository.ReleasesBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Backfill único para corrigir a bandeira (flag) de lançamentos bancários CNAB240
 * (Santander/Itaú/Bradesco) já importados antes da correção de
 * {@link BankStatementClassifierService#resolveFlag}, que casava por erp_code como substring
 * numérica solta no texto — um código de 1-2 dígitos (ex.: American Express=3) quase sempre
 * coincidia com algum dígito do PV/referência do lançamento, mascarando bandeiras sem sinal
 * próprio (Cabal, Banescard, Hipercard...).
 *
 * Reaproveita o texto já persistido em {@code descriptionHistoricalBank}/
 * {@code documentComplementNumber}/{@code complementRelease} (a mesma fonte usada na importação
 * original — ver Cnab240FileProcessor.buildRelease) e roda a MESMA lógica de resolução já
 * corrigida — não é uma reimplementação paralela.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankStatementFlagReclassificationService {

  private final ReleasesBankRepository releasesBankRepository;
  private final BankStatementClassifierService bankStatementClassifierService;
  private final BankTextSignalResolver textSignalResolver;
  private final FlagRepository flagRepository;

  @Transactional
  public ReclassifyBankStatementFlagsResult reclassifyAll() {
    // Só os pendentes: a tabela cresce sem limite com o histórico, e lançamentos já
    // conciliados não bloqueiam nada agora — reclassificá-los também poderia demorar muito
    // sem necessidade prática imediata.
    List<ReleasesBankEntity> releases = releasesBankRepository
      .findPendingForFlagReclassification(StatusPaymentBankEnum.PENDING.getCode());

    // Carregada uma única vez: resolveFlag(text, flags) evita repetir flagRepository.findAll()
    // (2 consultas) a cada lançamento — antes rodava por linha, o que dominava o tempo total
    // em bases com muitos lançamentos pendentes.
    List<FlagEntity> allFlags = flagRepository.findAll();

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
      Optional<FlagEntity> resolved = bankStatementClassifierService.resolveFlag(normalized, allFlags);

      UUID currentFlagId = release.getFlag() != null ? release.getFlag().getId() : null;
      UUID resolvedFlagId = resolved.map(FlagEntity::getId).orElse(null);

      if (!Objects.equals(currentFlagId, resolvedFlagId)) {
        release.setFlag(resolved.orElse(null));
        toSave.add(release);
      }
      if (resolved.isEmpty()) {
        stillUnresolved++;
      }
    }

    if (!toSave.isEmpty()) {
      releasesBankRepository.saveAll(toSave);
    }

    log.info(
      "🔄 Reclassificação de bandeira (extrato bancário CNAB240): analisados={}, atualizados={}, semBandeiraResolvida={}",
      analyzed,
      toSave.size(),
      stillUnresolved
    );

    return new ReclassifyBankStatementFlagsResult(analyzed, toSave.size(), stillUnresolved);
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
