-- V2__decision_log_payload.sql
ALTER TABLE decision_log
    ADD COLUMN IF NOT EXISTS payload JSONB;

CREATE INDEX IF NOT EXISTS idx_dl_event_type
    ON decision_log (event_type);