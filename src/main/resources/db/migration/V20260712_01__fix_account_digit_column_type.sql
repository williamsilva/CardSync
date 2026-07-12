-- account_digit was created as INT, inconsistent with its sibling agency_digit (VARCHAR(5))
-- and with the String semantics used throughout the application (JPQL coalesce, trim-based
-- normalization). Existing numeric values convert losslessly to their text representation.
ALTER TABLE cs_banking_domicile MODIFY COLUMN account_digit VARCHAR(5) NULL;
