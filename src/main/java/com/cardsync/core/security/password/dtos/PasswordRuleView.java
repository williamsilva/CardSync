package com.cardsync.core.security.password.dtos;

/**
 * View server-driven para UI.
 * state: OK | FAIL | PENDING
 */
public record PasswordRuleView(String code, String label, String state) {}
