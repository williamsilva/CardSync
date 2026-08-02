package com.cardsync.core.file.bank;

import com.cardsync.bff.controller.v1.representation.model.bank.ReleasesBankManualImportResult;
import com.cardsync.domain.exception.BusinessException;
import com.cardsync.domain.exception.ErrorCode;
import com.cardsync.domain.filter.ReleasesBankManualInput;
import com.cardsync.domain.filter.ReleasesBankManualTextImportInput;
import com.cardsync.domain.model.AcquirerEntity;
import com.cardsync.domain.model.EstablishmentEntity;
import com.cardsync.domain.model.FlagEntity;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.repository.AcquirerRepository;
import com.cardsync.domain.repository.FlagRepository;
import com.cardsync.domain.service.ReleasesBankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Cria um lançamento bancário manual a partir de UMA linha de extrato em texto livre (ex.:
 * exportação .txt do Itaú: "data;histórico;valor;"), classificando adquirente/bandeira/
 * modalidade a partir do próprio texto — mesma regra de BankTextSignalResolver/
 * BankStatementClassifierService já usada na importação automática (CNAB), reaproveitada aqui
 * em vez de reimplementada, para que uma correção de sinal (ex.: nova abreviação de bandeira)
 * valha para os dois fluxos.
 *
 * Restrito ao mesmo escopo da tela "Lançamento Bancário Manual" — só RECEIPT/cartão
 * (CASH_DEBIT/CASH_CREDIT, ver allModalityPaymentBankEnum no front): linhas de PIX, boleto ou
 * qualquer outra sem adquirente reconhecível são rejeitadas (400), não silenciosamente ignoradas
 * — o preview do front mostra a linha como erro para o usuário decidir (linhas que não são
 * venda em cartão simplesmente não pertencem a este fluxo).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualBankStatementTextImportService {

  private final FlagRepository flagRepository;
  private final AcquirerRepository acquirerRepository;
  private final ReleasesBankService releasesBankService;
  private final BankTextSignalResolver textSignalResolver;
  private final BankStatementClassifierService classifierService;

  @Transactional
  public ReleasesBankManualImportResult classifyAndCreate(ReleasesBankManualTextImportInput input) {
    String normalized = textSignalResolver.normalize(input.description());

    AcquirerEntity acquirer = classifierService
      .resolveAcquirer(normalized, acquirerRepository.findAll())
      .orElseThrow(() -> BusinessException.badRequest(
        ErrorCode.BUSINESS_ERROR,
        "Adquirente não identificado no texto do lançamento: " + input.description()
      ));

    ModalityPaymentBankEnum modality = resolveCardModality(normalized)
      .orElseThrow(() -> BusinessException.badRequest(
        ErrorCode.BUSINESS_ERROR,
        "Lançamento não reconhecido como recebimento de cartão (débito/crédito): " + input.description()
      ));

    Optional<FlagEntity> flag = classifierService.resolveFlag(normalized, flagRepository.findAll());
    List<Integer> pvCandidates = textSignalResolver.extractPvCandidates(input.description());
    Optional<EstablishmentEntity> establishment = classifierService.resolveEstablishment(pvCandidates, acquirer);

    var created = releasesBankService.createManual(new ReleasesBankManualInput(
      input.companyId(),
      input.bankingDomicileId(),
      input.releaseDate(),
      input.releaseValue(),
      ReleaseCategoryEnum.RECEIPT,
      modality,
      input.description(),
      null,
      null,
      acquirer.getId(),
      establishment.map(EstablishmentEntity::getId).orElse(null),
      flag.map(FlagEntity::getId).orElse(null)
    ));

    log.info(
      "✅ Lançamento bancário manual importado de texto: id={}, adquirente={}, bandeira={}, modalidade={}",
      created.id(), acquirer.getFantasyName(), flag.map(FlagEntity::getName).orElse(null), modality
    );

    return new ReleasesBankManualImportResult(
      created.id(),
      acquirer.getFantasyName(),
      flag.map(FlagEntity::getName).orElse(null),
      modality,
      establishment.map(EstablishmentEntity::getPvNumber).orElse(null)
    );
  }

  /**
   * PIX precisa ser checado antes (e rejeitado): o marcador de recebimento PIX de alguns bancos
   * contém um "CRED" isolado que também é sinal válido de cartão de crédito (ver
   * BankStatementClassifierService#resolveBankModality) — checar débito/crédito primeiro
   * classificaria PIX como venda em cartão.
   */
  private Optional<ModalityPaymentBankEnum> resolveCardModality(String normalizedText) {
    if (textSignalResolver.isPixReceiptSignal(normalizedText) || textSignalResolver.isPixSentSignal(normalizedText)) {
      return Optional.empty();
    }
    if (textSignalResolver.isDebitSignal(normalizedText)) return Optional.of(ModalityPaymentBankEnum.CASH_DEBIT);
    if (textSignalResolver.isCreditSignal(normalizedText)) return Optional.of(ModalityPaymentBankEnum.CASH_CREDIT);
    return Optional.empty();
  }
}
