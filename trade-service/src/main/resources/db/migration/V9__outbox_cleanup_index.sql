-- The retention delete used to range-scan every PUBLISHED row through the dispatch index and,
-- under REPEATABLE READ, next-key-lock the boundary that new PENDING inserts land in. That
-- stalled order writes for 7-14 s each minute under load. With this index it only touches rows
-- older than the cutoff.
CREATE INDEX idx_outbox_created ON outbox_event (created_at);
