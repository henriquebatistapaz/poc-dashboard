ALTER TABLE collection_run ADD COLUMN scheduled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE collection_run ADD COLUMN encrypted_input VARCHAR(8192);
CREATE INDEX idx_collection_run_schedule ON collection_run (status, scheduled_at);
