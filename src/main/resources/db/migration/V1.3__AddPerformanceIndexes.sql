-- Add performance indexes

-- Composite index for transaction queries (user + timestamp range)
ALTER TABLE transaction 
ADD INDEX idx_user_timestamp (user_id, timestamp);

-- Index for symbol lookups
ALTER TABLE transaction 
ADD INDEX idx_symbol (symbol);

-- Composite index for user + transaction type queries
ALTER TABLE transaction 
ADD INDEX idx_user_type (user_id, transaction_type_id);

-- Index on transaction_type description
ALTER TABLE transaction_type 
ADD INDEX idx_description (description);

-- Analyze tables to update statistics
ANALYZE TABLE user;
ANALYZE TABLE transaction;
ANALYZE TABLE transaction_type;
