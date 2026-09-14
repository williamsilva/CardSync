package com.cardsync.core.file.service;

import com.cardsync.domain.model.InstallmentAcqEntity;
import com.cardsync.domain.model.InstallmentUnschedulingEntity;
import com.cardsync.domain.model.enums.StatusInstallmentEnum;
import com.cardsync.domain.repository.InstallmentAcqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Achado real (auditoria 2026-09-13): InstallmentUnschedulingEntity (Rede EEVD registro "08" -
 * motivo 01 = chargeback, 00 = cancelamento) nunca tinha vínculo com a InstallmentAcqEntity real
 * que ela descreve - a adquirente avisa que uma parcela foi retirada do cronograma, mas esse
 * aviso ficava só armazenado pra relatório (ChargebacksService), nunca refletido na parcela que
 * a conciliação bancária de fato usa (o desagendamento nunca marcava a parcela como cancelada).
 *
 * Débito à vista tem sempre exatamente 1 parcela por venda, então acquirer+nsu já identifica a
 * InstallmentAcqEntity certa sem precisar de número de parcela (que este registro nem traz).
 * Correlaciona só quando exatamente uma parcela é encontrada - ambíguo (0 ou 2+) fica sem tocar,
 * mais seguro que arriscar cancelar a parcela errada.
 *
 * Escopo desta correção: só o registro "08" da Rede EEVD. Os registros "049"/"069"/"057" do
 * layout EEFI (crédito, onde o número da parcela importa de verdade) ficam fora - mesmo padrão
 * de risco, mas exigem correlação por número de parcela, não coberta aqui.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstallmentUnschedulingLinkingService {

  private final InstallmentAcqRepository installmentAcqRepository;

  @Transactional
  public LinkResult linkSavedUnschedulings(Collection<InstallmentUnschedulingEntity> unschedulings) {
    if (unschedulings == null || unschedulings.isEmpty()) {
      return new LinkResult(0, 0, 0);
    }

    int analyzed = 0;
    int canceled = 0;
    int ambiguous = 0;
    Map<UUID, InstallmentAcqEntity> installmentsToUpdate = new LinkedHashMap<>();

    for (InstallmentUnschedulingEntity unscheduling : unschedulings) {
      if (unscheduling == null) {
        continue;
      }

      analyzed++;

      if (unscheduling.getAcquirer() == null || unscheduling.getAcquirer().getId() == null || unscheduling.getNsu() == null) {
        continue;
      }

      List<InstallmentAcqEntity> matches = installmentAcqRepository.findByAcquirerIdAndTransactionNsu(
        unscheduling.getAcquirer().getId(), unscheduling.getNsu()
      );

      if (matches.size() != 1) {
        if (matches.size() > 1) {
          ambiguous++;
        }
        continue;
      }

      InstallmentAcqEntity installment = matches.get(0);
      if (Objects.equals(installment.getInstallmentStatus(), StatusInstallmentEnum.CANCELED.getCode())) {
        continue;
      }

      installment.setInstallmentStatus(StatusInstallmentEnum.CANCELED.getCode());
      installment.setCancellationDate(unscheduling.getTransactionDate());
      installmentsToUpdate.put(installment.getId(), installment);
      canceled++;
    }

    if (!installmentsToUpdate.isEmpty()) {
      installmentAcqRepository.saveAll(installmentsToUpdate.values());
    }

    if (ambiguous > 0) {
      log.warn("⚠ Desagendamento(s) com acquirer+nsu correspondendo a mais de uma InstallmentAcqEntity - deixados sem vínculo. ambiguous={}", ambiguous);
    }

    return new LinkResult(analyzed, canceled, ambiguous);
  }

  public record LinkResult(int analyzed, int canceled, int ambiguous) {}
}
