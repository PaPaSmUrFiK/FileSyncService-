-- migrations/001_init.sql

CREATE TABLE IF NOT EXISTS storage_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id UUID UNIQUE NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    hash VARCHAR(64) NOT NULL,
    stored_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS storage_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id UUID NOT NULL,
    version INTEGER NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    stored_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(file_id, version)
);

CREATE INDEX IF NOT EXISTS idx_storage_mappings_file_id ON storage_mappings(file_id);
CREATE INDEX IF NOT EXISTS idx_storage_versions_file_id ON storage_versions(file_id);
