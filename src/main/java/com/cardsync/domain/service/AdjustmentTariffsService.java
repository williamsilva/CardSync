package com.cardsync.domain.service;

import com.cardsync.bff.controller.v1.mapper.model.AdjustmentTariffsModelAssembler;
import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentTariffsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.ValueTotalsModel;
import com.cardsync.domain.filter.AdjustmentFilter;
import com.nimbussystems.commons.legacy.filter.query.ListQueryDto;
import com.cardsync.domain.model.AdjustmentEntity;
import com.cardsync.domain.repository.AdjustmentRepository;
import com.cardsync.domain.service.support.ValueTotalsQueryService;
import com.cardsync.infrastructure.repository.spec.AdjustmentTariffsSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdjustmentTariffsService {

  private final AdjustmentTariffsSpecs adjustmentSpecs;
  private final AdjustmentRepository adjustmentRepository;
  private final ValueTotalsQueryService totalsQueryService;
  private final AdjustmentTariffsModelAssembler adjustmentModelAssembler;

  /**
   * Busca paginada de ajustes/tarifas bancárias.
   *
   * <p>COUNT usa spec sem fetch joins — evita COUNT(DISTINCT id) com JOINs desnecessários.
   * DATA usa spec com fetch joins — carrega empresa, adquirente, bandeira e estabelecimento
   * em uma única query com DISTINCT.
   */
  @Transactional(readOnly = true)
  public Page<AdjustmentTariffsModel> search(Pageable pageable, ListQueryDto<AdjustmentFilter> query) {
    Specification<AdjustmentEntity> filterSpec = adjustmentSpecs.fromQueryForTotals(query);
    Specification<AdjustmentEntity> dataSpec   = adjustmentSpecs.fromQuery(query);

    long total = adjustmentRepository.count(filterSpec);

    // Pageable só de page/size (sem sort): dataSpec já monta o ORDER BY completo via
    // orderByTableSort/tableSort (com os aliases de colunas ligadas por join).
    // SimpleJpaRepository#findAll(Specification, Pageable) reaplica pageable.getSort() por
    // cima, resolvendo o nome bruto direto contra AdjustmentEntity (sem conhecer os aliases)
    // — sort por qualquer coluna que não seja campo direto da entidade quebra com "No property
    // 'X' found for type 'AdjustmentEntity'" (mesmo padrão de CreditOrderService/
    // AnticipationService, achado real 2026-09-10). O pageable original (com sort) continua
    // sendo usado só pro metadado da resposta (PageImpl abaixo).
    Pageable pageableWithoutSort = pageable.isPaged()
      ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
      : Pageable.unpaged();

    List<AdjustmentTariffsModel> content = total == 0
      ? List.of()
      : adjustmentRepository.findAll(dataSpec, pageableWithoutSort)
      .stream()
      .map(adjustmentModelAssembler::toModel)
      .toList();

    return new PageImpl<>(content, pageable, total);
  }

  @Transactional(readOnly = true)
  public ValueTotalsModel totals(ListQueryDto<AdjustmentFilter> query) {
    Specification<AdjustmentEntity> spec = adjustmentSpecs.fromQueryForTotals(query);
    return totalsQueryService.totals(AdjustmentEntity.class, spec,"adjustmentValue");
  }
}