-- One index for both Outbox access paths:
--   dispatcher: status = 'PENDING' ORDER BY created_at LIMIT 500 -> reads ~500 index entries.
--     The old (status, next_attempt_at, created_at) index needed a filesort over the whole
--     backlog, and the V9 created_at index made MySQL walk every PUBLISHED row first (6 s per
--     poll with a 117k backlog, so the publisher fell further behind the more it lagged).
--   retention delete: status = 'PUBLISHED' AND created_at < cutoff -> touches only expired rows,
--     so its next-key locks never reach the PENDING range new events are inserted into.
ALTER TABLE outbox_event
    DROP INDEX idx_outbox_dispatch,
    DROP INDEX idx_outbox_created,
    ADD INDEX idx_outbox_status_created (status, created_at, next_attempt_at);
