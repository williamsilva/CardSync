package com.cardsync.core.security.password.dtos;

import java.util.List;

public record PasswordRulesViewModel(
  boolean ok,
  int minLen,
  int historySize,
  List<PasswordRuleView> rules
) {}
