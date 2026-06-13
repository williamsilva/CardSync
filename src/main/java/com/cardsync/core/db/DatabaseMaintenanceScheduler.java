package com.cardsync.core.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduler de manutenção do banco de dados.
 *
 * <p>Executa {@code ANALYZE TABLE} periodicamente nas tabelas de maior
 * volume para manter as estatísticas do otimizador MySQL atualizadas.
 *
 * <p>Sem estatísticas recentes, o MySQL pode ignorar índices novos ou
 * preferir full scan — especialmente após importações em lote que inserem
 * muitas linhas rapidamente.
 *
 * <p>Cron padrão: domingo às 02h (horário de Brasília).
 * Configurável via variável de ambiente {@code ANALYZE_TABLE_CRON}.
 * Para desativar: {@code ANALYZE_TABLE_ENABLED=false}.
 */
@Slf4j
@Component
public class DatabaseMaintenanceScheduler {

  private static final List<String> TABLES_TO_ANALYZE = List.of(
    "cs_transaction_acq",
    "cs_transaction_erp",
    "cs_installment_acq",
    "cs_installment_erp",
    "cs_sales_summary",
    "cs_credit_order",
    "cs_releases_bank"
  );

  @PersistenceContext
  private EntityManager entityManager;

  @Transactional
  @Scheduled(
    cron = "${cardsync.maintenance.analyze-table-cron:0 0 2 * * SUN}",
    zone = "${cardsync.app.business-zone:America/Sao_Paulo}"
  )
  public void analyzeHighVolumeTables() {
    if (!isEnabled()) {
      return;
    }

    log.info("Iniciando ANALYZE TABLE nas tabelas de alto volume...");
    long start = System.currentTimeMillis();

    for (String table : TABLES_TO_ANALYZE) {
      try {
        entityManager.createNativeQuery("ANALYZE TABLE `" + table + "`").getResultList();
        log.debug("  ANALYZE TABLE {} concluido", table);
      } catch (Exception ex) {
        log.warn("  ANALYZE TABLE {} falhou: {}", table, ex.getMessage());
      }
    }

    log.info("ANALYZE TABLE concluido em {}ms para {} tabelas.",
      System.currentTimeMillis() - start, TABLES_TO_ANALYZE.size());
  }

  private boolean isEnabled() {
    String value = System.getenv("ANALYZE_TABLE_ENABLED");
    return value == null || !"false".equalsIgnoreCase(value.trim());
  }
}