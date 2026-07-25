package com.cardsync.bff.controller.v1.representation.model.bank;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.bankingdomicile.BankingDomicileMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import com.cardsync.domain.model.enums.ModalityPaymentBankEnum;
import com.cardsync.domain.model.enums.ReleaseCategoryEnum;
import com.cardsync.domain.model.enums.StatusPaymentBankEnum;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class ReleasesBankModel extends RepresentationModel<@NonNull ReleasesBankModel> {

  private UUID id;

  private Integer lineNumber;
  private Integer historicalCodeBank;

  private String documentComplementNumber;
  private String descriptionHistoricalBank;

  private LocalDate releaseDate;
  private BigDecimal releaseValue;

  private ReleaseCategoryEnum releaseCategory;
  private StatusPaymentBankEnum statusPaymentBank;
  private ModalityPaymentBankEnum modalityPaymentBank;

  /** Diferença aceita na conciliação manual (ver ManualBankReconciliationService.reconcile), com o motivo informado. Null quando o vínculo bateu exato. */
  private BigDecimal divergenceValue;
  private String divergenceReason;

  private FlagMinimalModel flag;
  private BankMinimalModel bank;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private EstablishmentMinimalModel establishment;
  private ProcessedFileMinimalModel processedFile;
  private BankingDomicileMinimalModel bankingDomicile;
}