package com.cardsync.bff.controller.v1.representation.model.conciliation;

import java.util.List;
import java.util.UUID;

/** releaseBankIds nulo/vazio = aplica a todos os lançamentos elegíveis (comportamento antigo). */
public record ApplyPreImplantationDivergenceRequest(List<UUID> releaseBankIds) {}
