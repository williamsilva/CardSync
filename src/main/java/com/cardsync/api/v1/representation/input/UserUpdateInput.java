package com.cardsync.api.v1.representation.input;

import java.util.Set;
import java.util.UUID;

public record UserUpdateInput(
  String name,
  Integer status,
  String document,
  Set<UUID> groupIds
) {}
