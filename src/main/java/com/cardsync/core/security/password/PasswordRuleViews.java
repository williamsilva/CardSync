package com.cardsync.core.security.password;

import com.cardsync.core.security.password.dtos.PasswordRuleView;
import com.cardsync.domain.model.enums.PasswordRuleCode;
import java.util.*;

/**
 * Helper para montar a lista de regras em ordem fixa e com labels amigáveis.
 *
 * failedCodes: regras falhadas
 * pendingCodes: regras que ainda não podem ser avaliadas (ex: sem username/confirm)
 */
public final class PasswordRuleViews {
  private PasswordRuleViews() {}

  public static List<PasswordRuleView> fromCodes(
    Set<String> enabledCodes,
    Set<String> failedCodes,
    Set<String> pendingCodes,
    int minLen,
    int historySize,
    Labels labels
  ) {
    // ordem “opiniosa” (igual ao exemplo do usuário)
    var list = new ArrayList<PasswordRuleView>();

    add(list, enabledCodes, PasswordRuleCode.PASSWORD_TOO_SHORT, labels.minLen(minLen), stateOf(PasswordRuleCode.PASSWORD_TOO_SHORT, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_MISSING_LOWER, labels.lower(), stateOf(PasswordRuleCode.PASSWORD_MISSING_LOWER, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_MISSING_UPPER, labels.upper(), stateOf(PasswordRuleCode.PASSWORD_MISSING_UPPER, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_MISSING_DIGIT, labels.digit(), stateOf(PasswordRuleCode.PASSWORD_MISSING_DIGIT, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_MISSING_SPECIAL, labels.special(), stateOf(PasswordRuleCode.PASSWORD_MISSING_SPECIAL, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_HAS_WHITESPACE, labels.noWhitespace(), stateOf(PasswordRuleCode.PASSWORD_HAS_WHITESPACE, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_HAS_SEQUENCE, labels.noSequence(), stateOf(PasswordRuleCode.PASSWORD_HAS_SEQUENCE, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_HAS_REPETITION, labels.noRepetition(), stateOf(PasswordRuleCode.PASSWORD_HAS_REPETITION, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_COMMON, labels.common(), stateOf(PasswordRuleCode.PASSWORD_COMMON, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_CONTAINS_USERNAME, labels.noUsername(), stateOf(PasswordRuleCode.PASSWORD_CONTAINS_USERNAME, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_NOT_MATCH, labels.match(), stateOf(PasswordRuleCode.PASSWORD_NOT_MATCH, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_REUSED_FROM_HISTORY, labels.history(historySize), stateOf(PasswordRuleCode.PASSWORD_REUSED_FROM_HISTORY, failedCodes, pendingCodes));
    add(list, enabledCodes, PasswordRuleCode.PASSWORD_SAME_AS_CURRENT, labels.sameAsCurrent(), stateOf(PasswordRuleCode.PASSWORD_SAME_AS_CURRENT, failedCodes, pendingCodes));

    return list;
  }

  private static void add(List<PasswordRuleView> out, Set<String> enabledCodes, PasswordRuleCode code, String label, String state) {
    if (enabledCodes == null || !enabledCodes.contains(code.name())) return;
    out.add(new PasswordRuleView(code.name(), label, state));
  }

  private static String stateOf(PasswordRuleCode code, Set<String> failed, Set<String> pending) {
    if (failed != null && failed.contains(code.name())) return "FAIL";
    if (pending != null && pending.contains(code.name())) return "PENDING";
    return "OK";
  }

  public interface Labels {
    String minLen(int minLen);
    String lower();
    String upper();
    String digit();
    String special();
    String noWhitespace();
    String noSequence();
    String noRepetition();
    String common();
    String noUsername();
    String match();
    String history(int historySize);
    String sameAsCurrent();
  }
}
