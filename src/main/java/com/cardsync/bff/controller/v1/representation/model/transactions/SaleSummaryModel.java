package com.cardsync.bff.controller.v1.representation.model.transactions;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.fileprocessing.ProcessedFileMinimalModel;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class SaleSummaryModel extends RepresentationModel<@NonNull SaleSummaryModel> {

  private UUID id;

  private Integer rvNumber;
  private Integer pvNumber;
  private Integer modality;
  private Integer lineNumber;
  private Integer numberCvNsu;

  private String creditOrderStatus;
  private String statusPaymentBank;
  private String transactionsStatus;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal adjustedValue;
  private BigDecimal discountValue;

  private LocalDate rvDate;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private ProcessedFileMinimalModel processedFile;

  private List<AdjustmentMinimalModel> adjustments = new ArrayList<>();
  private List<CreditOrderMinimalModel> creditOrders = new ArrayList<>();
}
