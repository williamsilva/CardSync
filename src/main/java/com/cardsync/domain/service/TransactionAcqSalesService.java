package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.TransactionsAcqModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionTotalsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.TransactionsAcqModel;
import com.cardsync.domain.filter.TransactionAcqSalesFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.model.TransactionAcqEntity;
import com.cardsync.domain.repository.TransactionAcqRepository;
import com.cardsync.infrastructure.repository.spec.TransactionAcqSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TransactionAcqSalesService {

  private final TransactionAcqSpecs transactionAcqSpecs;
  private final TransactionAcqRepository transactionAcqRepository;
  private final TransactionsAcqModelAssembler transactionsAcqModelAssembler;

  @Transactional(readOnly = true)
  public Page<TransactionsAcqModel> search(Pageable pageable, ListQueryDto<TransactionAcqSalesFilter> query) {
    Specification<TransactionAcqEntity> spec = transactionAcqSpecs.fromQuery(query);

    // A ordenação da tela ERP pode usar campos virtuais/com join, como expectedPaymentDate.
    // Por isso ela é aplicada dentro da Specification. O Pageable precisa seguir sem Sort,
    // senão o Spring Data tenta resolver expectedPaymentDate como atributo direto da entidade.
    Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

    return transactionAcqRepository
      .findAll(spec, unsortedPageable)
      .map(transactionsAcqModelAssembler::toModel);
  }

  @Transactional(readOnly = true)
  public TransactionTotalsModel totals(ListQueryDto<TransactionAcqSalesFilter> query) {
    Specification<TransactionAcqEntity> spec = transactionAcqSpecs.fromQuery(query);

    var rows = transactionAcqRepository.findAll(spec);

    BigDecimal totalGross = rows.stream()
      .map(TransactionAcqEntity::getGrossValue)
      .filter(Objects::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalFee = rows.stream()
      .map(TransactionAcqEntity::getDiscountValue)
      .filter(Objects::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalNet = rows.stream()
      .map(TransactionAcqEntity::getLiquidValue)
      .filter(Objects::nonNull)
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalAdjustments = rows.stream()
      .map(TransactionAcqEntity::getAdjustment)
      .filter(Objects::nonNull)
      .map(item -> item.getAdjustmentValue() == null ? BigDecimal.ZERO : item.getAdjustmentValue())
      .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new TransactionTotalsModel(totalGross, totalFee, totalNet, totalAdjustments, rows.size());
  }
}
