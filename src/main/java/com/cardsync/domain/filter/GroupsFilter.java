package com.cardsync.domain.filter;

public record GroupsFilter(
  String name,
  String description,

  String createdAtTo,
  String createdAtFrom
) {}
