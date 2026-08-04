/*
 * Correção de escopo da V20260804_03: as taxas informadas pelo usuário (print do portal Cielo)
 * valem SOMENTE pra PV 1051583117 — não pros outros dois PVs Cielo (1018802468, 1100125202), que
 * receberam o mesmo cadastro por engano.
 *
 * Confirmado com dado real: a taxa MDR real das vendas Elo do PV 1018802468 (2,15% débito / 5,30%
 * crédito à vista) diverge sistematicamente da tabela cadastrada (1,99%/3,37%) — já o PV
 * 1051583117 bateu exato (3,34% real vs 3,37% cadastrado). Ou seja, o PV 1018802468 tem uma taxa
 * negociada diferente (ainda não informada), e a tabela do usuário não deveria ter sido aplicada
 * a ele.
 *
 * Remove os contratos/bandeiras/taxas dos dois PVs errados; mantém intacto o de PV 1051583117
 * (criado pela V20260804_03, já correto).
 */

DELETE FROM cs_contract_rates
WHERE contract_flag_id IN (
  SELECT cf.id
  FROM cs_contract_flags cf
  JOIN cs_contracts c ON c.id = cf.contract_id
  JOIN cs_establishment e ON e.id = c.establishment_id
  WHERE e.pv_number IN (1018802468, 1100125202)
    AND c.acquirer_id = (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo')
);

DELETE FROM cs_contract_flags
WHERE contract_id IN (
  SELECT c.id
  FROM cs_contracts c
  JOIN cs_establishment e ON e.id = c.establishment_id
  WHERE e.pv_number IN (1018802468, 1100125202)
    AND c.acquirer_id = (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo')
);

DELETE FROM cs_contracts
WHERE id IN (
  SELECT c.id
  FROM cs_contracts c
  JOIN cs_establishment e ON e.id = c.establishment_id
  WHERE e.pv_number IN (1018802468, 1100125202)
    AND c.acquirer_id = (SELECT id FROM cs_acquirer WHERE file_identifier = 'Cielo')
);
