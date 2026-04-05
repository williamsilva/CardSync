package com.cardsync.domain.filter;

import java.util.List;

public record GroupsFilter(
  String name,
  String description,

  List<String> createdBy,

  String createdAtTo,
  String createdAtFrom
) {}
