-- migrations/001_init.up.sql

CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_name VARCHAR(255) NOT NULL,
    device_type VARCHAR(50) NOT NULL, -- desktop, mobile, tablet
    os VARCHAR(50),
    os_version VARCHAR(50),
    sync_token VARCHAR(500) UNIQUE NOT NULL,
    last_sync_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sync_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID UNIQUE NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    sync_cursor VARCHAR(100),
    last_sync_timestamp TIMESTAMP,
    files_synced INTEGER DEFAULT 0,
    pending_changes INTEGER DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS change_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_id VARCHAR(100) UNIQUE NOT NULL,
    file_id UUID NOT NULL,
    user_id UUID NOT NULL,
    device_id UUID NOT NULL,
    change_type VARCHAR(20) NOT NULL, -- created, modified, deleted, renamed
    file_path VARCHAR(1000),
    file_hash VARCHAR(64),
    file_size BIGINT,
    version INTEGER NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices(user_id);
CREATE INDEX IF NOT EXISTS idx_sync_state_device_id ON sync_state(device_id);
CREATE INDEX IF NOT EXISTS idx_change_log_user_id ON change_log(user_id);
CREATE INDEX IF NOT EXISTS idx_change_log_file_id ON change_log(file_id);
CREATE INDEX IF NOT EXISTS idx_change_log_timestamp ON change_log(timestamp);
