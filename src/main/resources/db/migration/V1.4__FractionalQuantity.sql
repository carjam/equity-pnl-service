-- Support fractional share quantities (e.g. from 3:2 splits, dollar-cost-average purchases,
-- or stock dividends). DECIMAL(20, 8) allows up to 20 significant digits with 8 decimal places,
-- which is sufficient for any broker-supported fractional share precision.
ALTER TABLE transaction MODIFY COLUMN quantity DECIMAL(20, 8) NULL;
