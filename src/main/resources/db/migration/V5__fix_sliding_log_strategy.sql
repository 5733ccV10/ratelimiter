-- SLIDING_LOG was the original enum name; renamed to SLIDING_WINDOW in code.
-- Backfill any rows that still carry the old value so Hibernate enum mapping doesn't throw on load.
UPDATE policies
SET strategy = 'SLIDING_WINDOW'
WHERE strategy = 'SLIDING_LOG';
