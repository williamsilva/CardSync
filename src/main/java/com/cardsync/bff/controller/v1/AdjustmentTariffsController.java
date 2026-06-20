package com.cardsync.bff.controller.v1;

import com.cardsync.bff.controller.v1.representation.model.transactions.AdjustmentTariffsModel;
import com.cardsync.bff.controller.v1.representation.model.transactions.ValueTotalsModel;
import com.cardsync.core.security.CheckSecurity;
import com.cardsync.domain.filter.AdjustmentFilter;
import com.cardsync.domain.filter.query.ListQueryDto;
import com.cardsync.domain.filter.support.PageableMapper;
import com.cardsync.domain.service.AdjustmentTariffsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.hateoas.PagedModel;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bff/v1/adjustments")
public class AdjustmentTariffsController {

  private final AdjustmentTariffsService adjustmentService;

  /**
   * Busca paginada de ajustes e tarifas bancárias.
   *
   * <p>Suporta os filtros exibidos na tela:
   * <ul>
   *   <li><b>tableFilters</b> — filtros de coluna do PrimeNG DataTable (data ajuste,
   *       data crédito, valor, NSU, autorização, motivo, status)
   *   <li><b>advanced</b> — filtros avançados (período, adquirente, bandeira,
   *       empresa, estabelecimento, faixas de valor, motivo do ajuste)
   *   <li><b>globalFilter</b> — busca livre por NSU ou autorização
   * </ul>
   *
   * <p>Retorna {@link PagedModel} com os metadados de paginação e os itens no campo
   * {@code content}, compatível com o componente de lista do frontend.
   */
  @PostMapping("/tariffs")
  @CheckSecurity.FileProcessing.CanRead
  public PagedModel<AdjustmentTariffsModel> search(@RequestBody ListQueryDto<AdjustmentFilter> body) {
    var pageable = PageableMapper.toPageable(body.page(), body.size(), body.sort());

    Page<AdjustmentTariffsModel> page = adjustmentService.search(pageable, body);

    return PagedModel.of(
      page.getContent(),
      new PagedModel.PageMetadata(
        page.getSize(),
        page.getNumber(),
        page.getTotalElements(),
        page.getTotalPages()
      )
    );
  }

  @PostMapping("/tariffs-totals")
  @CheckSecurity.FileProcessing.CanRead
  public ValueTotalsModel totals(@RequestBody ListQueryDto<AdjustmentFilter> body) {
    return adjustmentService.totals(body);
  }
}