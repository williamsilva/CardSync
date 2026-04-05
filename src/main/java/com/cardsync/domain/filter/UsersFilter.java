package com.cardsync.domain.filter;

import com.cardsync.domain.model.enums.StatusUserEnum;

import java.util.List;

public record UsersFilter(
  String name,
  String userName,
  String document,

  List<String> createdBy,
  List<StatusUserEnum> status,

  String createdAtTo,
  String createdAtFrom,
  String lastLoginAtTo,
  String blockedUntilTo,
  String lastLoginAtFrom,
  String blockedUntilFrom,
  String passwordExpiresAtTo,
  String passwordExpiresAtFrom
) {}
