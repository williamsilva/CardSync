-- cs_contracts.status and cs_establishment.pv_number were created as BIGINT, but the
-- application has always used Integer end-to-end for these fields (enum codes and PV
-- numbers respectively), matching the convention used by every other status/code column
-- in the schema (INT). cs_sales_summary.bank was created as INT, but it is populated by
-- parsing text bank codes straight from acquirer file layouts (e.g. "341", "033") and
-- treated as String throughout the codebase, matching cs_bank.code VARCHAR(10).
ALTER TABLE cs_contracts MODIFY COLUMN status INT NOT NULL;
ALTER TABLE cs_establishment MODIFY COLUMN pv_number INT NOT NULL;
ALTER TABLE cs_sales_summary MODIFY COLUMN bank VARCHAR(10) NULL;
