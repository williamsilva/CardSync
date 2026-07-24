package com.cardsync.core.file.service;

import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.model.SalesSummaryEntity;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.repository.SalesSummaryRepository;
import com.cardsync.domain.repository.TransactionAcqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustmentTransactionLinkService {

  private final AdjustmentRepository adjustmentRepository;
  private final TransactionAcqRepository transactionAcqRepository;
  private final SalesSummaryRepository salesSummaryRepository;

  @Transactional
  public LinkResult linkSavedAdjustments(Collection<AdjustmentEntity> adjustments) {
    if (adjustments == null || adjustments.isEmpty()) {
      return new LinkResult(0, 0, 0);
    }

    int analyzed = 0;
    int linked = 0;
    int linkedBySalesSummaryOnly = 0;

    Map<UUID, AdjustmentEntity> adjustmentsToUpdate = new LinkedHashMap<>();
    Map<UUID, TransactionAcqEntity> transactionsToUpdate = new LinkedHashMap<>();

    for (AdjustmentEntity adjustment : adjustments) {
      if (adjustment == null) {
        continue;
      }

      analyzed++;

      Optional<TransactionAcqEntity> transactionOpt = findTransaction(adjustment);

      if (transactionOpt.isEmpty()) {
        /*
         * Ajustes sem NSU (ex.: motivo 28 - aluguel POS/pinpad) nunca têm uma transação
         * específica para apontar - a Rede não amarra esse tipo de tarifa a um CV. O layout
         * EEFI, porém, preenche o RV/PV do ajuste independente do motivo (só o NSU é restrito
         * aos motivos 16/18/23), então ainda dá pra achar o resumo de vendas certo e evitar
         * que o ajuste fique órfão (sem salesSummary) na conciliação banco x adquirente.
         */
        Optional<SalesSummaryEntity> salesSummaryOpt = findSalesSummaryByRvAndPv(adjustment);

        if (salesSummaryOpt.isPresent()) {
          adjustment.setSalesSummary(salesSummaryOpt.get());
          if (adjustment.getId() != null) {
            adjustmentsToUpdate.put(adjustment.getId(), adjustment);
          }
          linkedBySalesSummaryOnly++;
          continue;
        }

        log.debug(
          "Ajuste não vinculado a transação nem a resumo de vendas. adjustmentId={}, recordType={}, nsu={}, authorization={}, pv={}, rvOriginal={}, rvAdjustment={}",
          adjustment.getId(),
          adjustment.getRecordType(),
          adjustment.getNsu(),
          adjustment.getAuthorization(),
          firstNonNull(adjustment.getPvNumberOriginal(), adjustment.getPvNumber(), adjustment.getPvNumberAdjustment()),
          adjustment.getRvNumberOriginal(),
          adjustment.getRvNumberAdjustment()
        );
        continue;
      }

      TransactionAcqEntity transaction = transactionOpt.get();

      adjustment.setTransaction(transaction);

      if (adjustment.getSalesSummary() == null) {
        adjustment.setSalesSummary(transaction.getSalesSummary());
      }

      if (adjustment.getCompany() == null) {
        adjustment.setCompany(transaction.getCompany());
      }

      if (adjustment.getEstablishment() == null) {
        adjustment.setEstablishment(transaction.getEstablishment());
      }

      if (adjustment.getAcquirer() == null) {
        adjustment.setAcquirer(transaction.getAcquirer());
      }

      if (adjustment.getRvFlagOrigin() == null) {
        if (transaction.getFlag() != null) {
          adjustment.setRvFlagOrigin(transaction.getFlag());
        } else if (transaction.getSalesSummary() != null && transaction.getSalesSummary().getFlag() != null) {
          adjustment.setRvFlagOrigin(transaction.getSalesSummary().getFlag());
        }
      }

      if (adjustment.getRvFlagAdjustment() == null && transaction.getFlag() != null) {
        adjustment.setRvFlagAdjustment(transaction.getFlag());
      }

      if (adjustment.getId() != null) {
        adjustmentsToUpdate.put(adjustment.getId(), adjustment);
      }

      /*
       * adjustment.transaction_id guarda o vínculo real do ajuste com a venda.
       * transaction.adjustment_id é mantido como ponteiro auxiliar para telas/assemblers que leem a partir da venda.
       * Como pode existir mais de um ajuste para a mesma transação, não sobrescrevemos quando já houver um ajuste vinculado.
       */
      if (transaction.getAdjustment() == null) {
        transaction.setAdjustment(adjustment);
        transactionsToUpdate.put(transaction.getId(), transaction);
      }

      linked++;
    }

    if (!adjustmentsToUpdate.isEmpty()) {
      adjustmentRepository.saveAll(adjustmentsToUpdate.values());
    }

    if (!transactionsToUpdate.isEmpty()) {
      transactionAcqRepository.saveAll(transactionsToUpdate.values());
    }

    return new LinkResult(analyzed, linked, linkedBySalesSummaryOnly);
  }

  private Optional<TransactionAcqEntity> findTransaction(AdjustmentEntity adjustment) {
    if (adjustment.getNsu() == null) {
      return Optional.empty();
    }

    String authorization = normalizeAuthorization(adjustment.getAuthorization());

    if (adjustment.getSalesSummary() != null && adjustment.getSalesSummary().getId() != null) {
      UUID salesSummaryId = adjustment.getSalesSummary().getId();

      if (authorization != null) {
        Optional<TransactionAcqEntity> bySummaryNsuAuth =
          transactionAcqRepository.findFirstBySalesSummary_IdAndNsuAndAuthorizationOrderBySaleDateDesc(
            salesSummaryId,
            adjustment.getNsu(),
            authorization
          );

        if (bySummaryNsuAuth.isPresent()) {
          return bySummaryNsuAuth;
        }
      }

      Optional<TransactionAcqEntity> bySummaryNsu =
        transactionAcqRepository.findFirstBySalesSummary_IdAndNsuOrderBySaleDateDesc(
          salesSummaryId,
          adjustment.getNsu()
        );

      if (bySummaryNsu.isPresent()) {
        return bySummaryNsu;
      }
    }

    if (adjustment.getAcquirer() != null && adjustment.getAcquirer().getId() != null) {
      Integer pvNumber = firstNonNull(
        adjustment.getPvNumberOriginal(),
        adjustment.getPvNumber(),
        adjustment.getPvNumberAdjustment()
      );

      Integer rvNumber = firstNonNull(
        adjustment.getRvNumberOriginal(),
        adjustment.getRvNumberInstallmentOriginal(),
        adjustment.getRvNumberAdjustment()
      );

      if (pvNumber != null && rvNumber != null) {
        if (authorization != null) {
          Optional<TransactionAcqEntity> byContextNsuAuth =
            transactionAcqRepository.findFirstByAcquirer_IdAndEstablishment_PvNumberAndRvNumberAndNsuAndAuthorizationOrderBySaleDateDesc(
              adjustment.getAcquirer().getId(),
              pvNumber,
              rvNumber,
              adjustment.getNsu(),
              authorization
            );

          if (byContextNsuAuth.isPresent()) {
            return byContextNsuAuth;
          }
        }

        Optional<TransactionAcqEntity> byContextNsu =
          transactionAcqRepository.findFirstByAcquirer_IdAndEstablishment_PvNumberAndRvNumberAndNsuOrderBySaleDateDesc(
            adjustment.getAcquirer().getId(),
            pvNumber,
            rvNumber,
            adjustment.getNsu()
          );

        if (byContextNsu.isPresent()) {
          return byContextNsu;
        }
      }
    }

    if (authorization != null) {
      Optional<TransactionAcqEntity> byNsuAuth =
        transactionAcqRepository.findFirstByNsuAndAuthorization(adjustment.getNsu(), authorization);

      if (byNsuAuth.isPresent()) {
        return byNsuAuth;
      }
    }

    return transactionAcqRepository.findFirstByNsu(adjustment.getNsu());
  }

  /*
   * Fallback para ajustes sem NSU (nenhuma transação "CV" pra apontar - ex.: aluguel de
   * POS/pinpad, motivo 28). O RV/PV do ajuste ainda identifica o resumo de vendas certo,
   * então usamos o mesmo par acquirer+pvNumber+rvNumber que o SalesSummaryRepository já
   * expõe (ver ProcessRedeEeFiService.safeSalesSummary) em vez de deixar o ajuste órfão.
   */
  private Optional<SalesSummaryEntity> findSalesSummaryByRvAndPv(AdjustmentEntity adjustment) {
    if (adjustment.getSalesSummary() != null) {
      return Optional.of(adjustment.getSalesSummary());
    }

    if (adjustment.getAcquirer() == null || adjustment.getAcquirer().getId() == null) {
      return Optional.empty();
    }

    Integer pvNumber = firstNonNull(
      adjustment.getPvNumberOriginal(),
      adjustment.getPvNumber(),
      adjustment.getPvNumberAdjustment()
    );

    Integer rvNumber = firstNonNull(
      adjustment.getRvNumberOriginal(),
      adjustment.getRvNumberInstallmentOriginal(),
      adjustment.getRvNumberAdjustment()
    );

    if (pvNumber == null || rvNumber == null) {
      return Optional.empty();
    }

    return salesSummaryRepository.findFirstByAcquirer_IdAndPvNumberAndRvNumberOrderByRvDateDesc(
      adjustment.getAcquirer().getId(),
      pvNumber,
      rvNumber
    );
  }

  private String normalizeAuthorization(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }

    return authorization.trim();
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    if (values == null) {
      return null;
    }

    for (T value : values) {
      if (value != null) {
        return value;
      }
    }

    return null;
  }

  public record LinkResult(int analyzed, int linked, int linkedBySalesSummaryOnly) {
  }
}
