-- migrations/002_conflicts.up.sql

CREATE TABLE IF NOT EXISTS sync_conflicts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id UUID NOT NULL,
    user_id UUID NOT NULL,
    conflict_type VARCHAR(50) NOT NULL, -- version_conflict, concurrent_edit
    device_a_id UUID NOT NULL,
    device_b_id UUID NOT NULL,
    version_a INTEGER NOT NULL,
    version_b INTEGER NOT NULL,
    resolved BOOLEAN DEFAULT FALSE,
    resolution_type VARCHAR(50), -- keep_both, keep_a, keep_b, manual
    conflict_file_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_sync_conflicts_file_id ON sync_conflicts(file_id);
CREATE INDEX IF NOT EXISTS idx_sync_conflicts_user_id ON sync_conflicts(user_id);
