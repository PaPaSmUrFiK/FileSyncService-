-- ============================================================
-- Migration V2: Update file_shares and file_versions
-- ============================================================
-- Changes:
-- 1. Remove owner_id from file_shares (redundant, source of truth is files.user_id)
-- 2. Add created_by to file_shares (audit: who shared the file)
-- 3. Rename created_by to created_by_user_id in file_versions (clarity)
-- 4. Add performance indexes
-- ============================================================

-- Step 1: Update file_shares table
-- Remove owner_id column (redundant)
ALTER TABLE file_shares DROP COLUMN IF EXISTS owner_id;

-- Add created_by column (who shared the file)
ALTER TABLE file_shares ADD COLUMN IF NOT EXISTS created_by UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

-- Remove expires_at (not in requirements)
ALTER TABLE file_shares DROP COLUMN IF EXISTS expires_at;

-- Add CHECK constraint for permission
ALTER TABLE file_shares DROP CONSTRAINT IF EXISTS check_file_shares_permission;
ALTER TABLE file_shares ADD CONSTRAINT check_file_shares_permission 
    CHECK (permission IN ('READ', 'WRITE'));

-- Step 2: Rename created_by to created_by_user_id in file_versions
-- This makes it clear that this is the user who created the version
ALTER TABLE file_versions RENAME COLUMN created_by TO created_by_user_id;

-- Step 3: Add performance indexes

-- Index for finding versions by creator
CREATE INDEX IF NOT EXISTS idx_file_versions_created_by 
    ON file_versions(created_by_user_id);

-- Index for version history (file_id + version DESC)
CREATE INDEX IF NOT EXISTS idx_file_versions_file_id_version 
    ON file_versions(file_id, version DESC);

-- Index for finding files by owner and deletion status
CREATE INDEX IF NOT EXISTS idx_files_owner_deleted 
    ON files(user_id, is_deleted);

-- Index for finding deleted files
CREATE INDEX IF NOT EXISTS idx_files_deleted_at 
    ON files(deleted_at) WHERE is_deleted = true;

-- Index for finding files by parent folder (excluding root)
DROP INDEX IF EXISTS idx_files_parent_folder;
CREATE INDEX idx_files_parent_folder 
    ON files(parent_folder_id) WHERE parent_folder_id IS NOT NULL;

-- Index for file_shares by creation date
CREATE INDEX IF NOT EXISTS idx_file_shares_created_at 
    ON file_shares(created_at DESC);

-- Step 4: Update existing data
-- Set created_by for existing shares to file owner
UPDATE file_shares fs
SET created_by = f.user_id
FROM files f
WHERE fs.file_id = f.id AND fs.created_by = '00000000-0000-0000-0000-000000000000';

-- Step 5: Remove default constraint from created_by
ALTER TABLE file_shares ALTER COLUMN created_by DROP DEFAULT;

-- ============================================================
-- Verification queries (commented out, for manual testing):
-- ============================================================
-- SELECT * FROM file_shares LIMIT 5;
-- SELECT * FROM file_versions LIMIT 5;
-- SELECT indexname, indexdef FROM pg_indexes WHERE tablename IN ('files', 'file_versions', 'file_shares');
