package com.cardsync.bff.controller.v1.representation.input;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record GroupPermissionsInput(@NotNull List<UUID> permissionIds) {}
