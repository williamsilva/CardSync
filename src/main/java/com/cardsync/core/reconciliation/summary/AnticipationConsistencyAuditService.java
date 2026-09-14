package com.cardsync.core.reconciliation.summary;

import com.cardsync.bff.controller.v1.representation.model.conciliation.AnticipationConsistencyAuditResult;
import com.cardsync.domain.repository.AnticipationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Achado real (auditoria 2026-09-13): quando ProcessRedeEeFiService/ProcessCielo15Service não
 * resolvem o domicílio bancário de uma antecipação (agência/conta não cadastrados), ela - e a
 * CreditOrder sintética gerada a partir dela na Etapa 6 - nunca concilia com o extrato bancário.
 * Antes, o único sinal disso era um log.warn no momento da importação, sem nenhuma forma de
 * encontrar depois quais antecipações ficaram travadas assim. Só leitura/alerta - a correção
 * (cadastrar o domicílio certo) é sempre manual, fora deste serviço.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnticipationConsistencyAuditService {

  private static final int SAMPLE_SIZE = 50;

  private final AnticipationRepository anticipationRepository;

  @Transactional(readOnly = true)
  public AnticipationConsistencyAuditResult audit() {
    long missingBankingDomicileCount = anticipationRepository.countMissingBankingDomicile();

    List<UUID> sampleIds = missingBankingDomicileCount == 0
      ? List.of()
      : anticipationRepository.findIdsMissingBankingDomicile(PageRequest.of(0, SAMPLE_SIZE));

    if (missingBankingDomicileCount > 0) {
      log.warn(
        "⚠ Auditoria de antecipações: {} antecipação(ões) com valor a liberar mas sem domicílio bancário resolvido - nunca vão conciliar com o banco. amostra={}",
        missingBankingDomicileCount, sampleIds
      );
    } else {
      log.info("✅ Auditoria de antecipações: nenhuma antecipação travada por domicílio bancário ausente.");
    }

    return new AnticipationConsistencyAuditResult(missingBankingDomicileCount, sampleIds);
  }
}
