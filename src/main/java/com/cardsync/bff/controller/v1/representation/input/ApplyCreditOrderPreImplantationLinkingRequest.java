package com.cardsync.bff.controller.v1.representation.input;

import java.util.List;
import java.util.UUID;

/** creditOrderIds nulo/vazio = aplica a todas as órfãs com vínculo exato disponível. */
public record ApplyCreditOrderPreImplantationLinkingRequest(List<UUID> creditOrderIds) {}
