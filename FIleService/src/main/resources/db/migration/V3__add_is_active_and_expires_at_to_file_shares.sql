-- ============================================================
-- Migration V3: Add is_active and expires_at to file_shares
-- ============================================================
-- Changes:
-- 1. Add is_active column to file_shares (default: true)
-- 2. Add expires_at column to file_shares (optional, nullable)
-- 3. Ensure created_by_user_id exists in file_versions (should already exist from V2)
-- ============================================================

-- Step 1: Add is_active column to file_shares
ALTER TABLE file_shares ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Step 2: Add expires_at column to file_shares (was removed in V2, but needed by code)
ALTER TABLE file_shares ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

-- Step 3: Verify created_by_user_id exists in file_versions (should exist from V2)
-- If the column doesn't exist (edge case), add it
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'file_versions' 
        AND column_name = 'created_by_user_id'
    ) THEN
        ALTER TABLE file_versions ADD COLUMN created_by_user_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';
        
        -- Update existing data (copy from created_by if exists, or use default)
        IF EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'file_versions' 
            AND column_name = 'created_by'
        ) THEN
            UPDATE file_versions SET created_by_user_id = created_by WHERE created_by_user_id = '00000000-0000-0000-0000-000000000000';
            ALTER TABLE file_versions DROP COLUMN created_by;
        END IF;
        
        -- Remove default after data migration
        ALTER TABLE file_versions ALTER COLUMN created_by_user_id DROP DEFAULT;
    END IF;
END $$;

