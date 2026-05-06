package com.cardsync.core.file.bank;

import lombok.Getter;

import java.util.Set;

@Getter
public enum Cnab240BankLayout {
  SANTANDER(
    "033", "Santander", "Santander",
    "52-57", "58-70", "70-71",
    "108-111", "111-113", "113-133", "133-134",
    "134-142", "142-150", "150-168", "168-169",
    "169-172", "172-176", "176-201", "201-240",
    false,
    Set.of("E")
  ),
  ITAU(
    "341", "Itau", "Itaú",
    "53-58", "65-70", "71-72",
    "108-111", "111-113", "113-133", "133-134",
    "134-142", "142-150", "150-168", "168-169",
    "169-172", "172-176", "176-201", "201-240",
    true,
    Set.of("E")
  ),
  BRADESCO(
    "237", "Bradesco", "Bradesco",
    "53-58", "65-70", "70-71",
    "108-111", "111-113", "113-133", "133-134",
    "134-142", "142-150", "150-168", "168-169",
    "169-172", "172-176", "176-201", "201-240",
    true,
    Set.of("E")
  );

  private final String bankCode;
  private final String originCode;
  private final String displayName;
  private final String agencyRange;
  private final String currentAccountRange;
  private final String digitAccountRange;
  private final String natureRange;
  private final String complementTypeRange;
  private final String complementRange;
  private final String cpmfRange;
  private final String accountingDateRange;
  private final String releaseDateRange;
  private final String releaseValueRange;
  private final String releaseTypeRange;
  private final String releaseCategoryRange;
  private final String historicalCodeRange;
  private final String descriptionRange;
  private final String documentRange;
  private final boolean usesDescriptionForModality;
  private final Set<String> supportedDetailSegments;

  Cnab240BankLayout(
    String bankCode,
    String originCode,
    String displayName,
    String agencyRange,
    String currentAccountRange,
    String digitAccountRange,
    String natureRange,
    String complementTypeRange,
    String complementRange,
    String cpmfRange,
    String accountingDateRange,
    String releaseDateRange,
    String releaseValueRange,
    String releaseTypeRange,
    String releaseCategoryRange,
    String historicalCodeRange,
    String descriptionRange,
    String documentRange,
    boolean usesDescriptionForModality,
    Set<String> supportedDetailSegments
  ) {
    this.bankCode = bankCode;
    this.originCode = originCode;
    this.displayName = displayName;
    this.agencyRange = agencyRange;
    this.currentAccountRange = currentAccountRange;
    this.digitAccountRange = digitAccountRange;
    this.natureRange = natureRange;
    this.complementTypeRange = complementTypeRange;
    this.complementRange = complementRange;
    this.cpmfRange = cpmfRange;
    this.accountingDateRange = accountingDateRange;
    this.releaseDateRange = releaseDateRange;
    this.releaseValueRange = releaseValueRange;
    this.releaseTypeRange = releaseTypeRange;
    this.releaseCategoryRange = releaseCategoryRange;
    this.historicalCodeRange = historicalCodeRange;
    this.descriptionRange = descriptionRange;
    this.documentRange = documentRange;
    this.usesDescriptionForModality = usesDescriptionForModality;
    this.supportedDetailSegments = supportedDetailSegments;
  }

  public boolean isSupportedDetailSegment(String segmentCode) {
    return segmentCode != null && supportedDetailSegments.contains(segmentCode.trim().toUpperCase());
  }

  public static Cnab240BankLayout fromBankCode(String code) {
    if (code == null) return null;
    String normalized = code.replaceAll("\\D", "");
    for (Cnab240BankLayout layout : values()) {
      if (layout.bankCode.equals(normalized)) return layout;
    }
    return null;
  }
}
