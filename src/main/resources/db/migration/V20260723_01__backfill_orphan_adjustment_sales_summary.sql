-- Backfill de cs_adjustment.sales_summary_id para ajustes que ficaram órfãos porque o
-- vínculo antigo (AdjustmentTransactionLinkService) exigia NSU para achar a transação e,
-- consequentemente, o resumo de vendas (RV). Ajustes sem transação associada (ex.: motivo 28
-- "AL. PÓS/PINPAD/TX CONECT" - aluguel de maquininha) nunca têm NSU no layout da Rede, mas o
-- RV/PV do ajuste é preenchido normalmente - dá pra achar o resumo de vendas certo sem
-- precisar de uma transação específica. Ver AdjustmentTransactionLinkService.findSalesSummaryByRvAndPv.
--
-- Escopo: só toca ajustes hoje sem sales_summary_id, sem NSU, e que não sejam um dos motivos
-- exclusivamente transacionais (16 = ESTORNOCR. INDEV. CI, 18 = CANCEL. DE VENDAS,
-- 23 = CONTESTAÇÃO DE VENDA - únicos que o layout EEFI preenche com NSU).
UPDATE cs_adjustment a
SET sales_summary_id = (
  SELECT ss.id
  FROM cs_sales_summary ss
  WHERE ss.acquirer_id = a.acquirer_id
    AND ss.pv_number = COALESCE(a.pv_number_original, a.pv_number, a.pv_number_adjustment)
    AND ss.rv_number = COALESCE(a.rv_number_original, a.rv_number_installment_original, a.rv_number_adjustment)
  ORDER BY ss.rv_date DESC
  LIMIT 1
)
WHERE a.sales_summary_id IS NULL
  AND a.nsu IS NULL
  AND a.acquirer_id IS NOT NULL
  AND (a.adjustment_reason IS NULL OR a.adjustment_reason NOT IN (16, 18, 23))
  AND COALESCE(a.pv_number_original, a.pv_number, a.pv_number_adjustment) IS NOT NULL
  AND COALESCE(a.rv_number_original, a.rv_number_installment_original, a.rv_number_adjustment) IS NOT NULL
  AND EXISTS (
    SELECT 1
    FROM cs_sales_summary ss
    WHERE ss.acquirer_id = a.acquirer_id
      AND ss.pv_number = COALESCE(a.pv_number_original, a.pv_number, a.pv_number_adjustment)
      AND ss.rv_number = COALESCE(a.rv_number_original, a.rv_number_installment_original, a.rv_number_adjustment)
  );
