UPDATE cs_reconciliation_settings
SET legacy_marking_months               = 12,
    bank_mark_not_reconciled_after_days  = 3,
    date_tolerance_days_before           = 0,
    date_tolerance_days_after            = 0,
    value_tolerance                      = 0.05,
    flag_match_required                  = true,
    establishment_match_required         = false,
    payment_kind_match_required          = false;
