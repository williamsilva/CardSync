package com.cardsync.core.reconciliation;

/**
 * Resultado da marcação de lançamentos bancários como legado (anteriores à
 * implantação): {@code updated} foram marcados; {@code skipped} foram ignorados
 * por não estarem pendentes (já conciliados, cancelados etc.).
 */
public record MarkLegacyResult(int updated, int skipped) {}
