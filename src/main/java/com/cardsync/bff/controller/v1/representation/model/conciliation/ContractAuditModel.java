package com.cardsync.bff.controller.v1.representation.model.conciliation;

import com.cardsync.bff.controller.v1.representation.model.AcquirerMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.CompanyMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.EstablishmentMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.FlagMinimalModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsAcqToContractModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsErpToContractModel;
import lombok.*;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Relation(collectionRelation = "content")
public class ContractAuditModel extends RepresentationModel<@NonNull ContractAuditModel>  {

  private UUID id;

  private Long cvNsu;

  private Integer status;
  private Integer capture;
  private Integer modality;

  private String authorization;

  private BigDecimal grossValue;
  private BigDecimal liquidValue;
  private BigDecimal rateAcquirer;
  private BigDecimal rateContract;
  private BigDecimal discountValue;
  private BigDecimal differenceValue;

  private FlagMinimalModel flag;
  private CompanyMinimalModel company;
  private AcquirerMinimalModel acquirer;
  private EstablishmentMinimalModel establishment;
  private TransactionsAcqToContractModel transactionAcq;
  private TransactionsErpToContractModel transactionErp;
}
