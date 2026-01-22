-- ============================================================
-- Migration V3: Add user blocking and admin features
-- ============================================================
-- Changes:
-- 1. Add blocking fields to users table
-- 2. Add last_login_at for admin statistics (synced from AuthService via Kafka)
-- 3. Add plan field to users table (moved from user_quotas for simplicity)
-- 4. Add indexes for admin queries
-- ============================================================

-- Step 1: Add blocking fields to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_blocked BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_reason TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_by UUID;

-- Step 2: Add last_login_at for statistics
-- Source of truth is AuthService, this is synced via Kafka events
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;

-- Step 3: Add plan field to users table
-- This simplifies queries and avoids JOIN with user_quotas
ALTER TABLE users ADD COLUMN IF NOT EXISTS plan VARCHAR(50) DEFAULT 'free' NOT NULL;

-- Add CHECK constraint for plan
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'check_users_plan') THEN
        ALTER TABLE users ADD CONSTRAINT check_users_plan 
            CHECK (plan IN ('free', 'premium', 'business', 'enterprise'));
    END IF;
END $$;

-- Step 4: Migrate existing plan data from user_quotas to users
UPDATE users u
SET plan = uq.plan_type
FROM user_quotas uq
WHERE u.id = uq.user_id;

-- Step 5: Add quota fields directly to users table
-- This denormalizes data but improves query performance
ALTER TABLE users ADD COLUMN IF NOT EXISTS max_file_size BIGINT DEFAULT 104857600 NOT NULL; -- 100MB
ALTER TABLE users ADD COLUMN IF NOT EXISTS max_devices INTEGER DEFAULT 3 NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS max_shares INTEGER DEFAULT 10 NOT NULL;

-- Migrate quota data
UPDATE users u
SET 
    max_file_size = uq.max_file_size,
    max_devices = uq.max_devices,
    max_shares = uq.max_shares
FROM user_quotas uq
WHERE u.id = uq.user_id;

-- Step 6: Add indexes for admin queries

-- Index for finding blocked users
CREATE INDEX IF NOT EXISTS idx_users_is_blocked 
    ON users(is_blocked) WHERE is_blocked = true;

-- Index for last login statistics
CREATE INDEX IF NOT EXISTS idx_users_last_login_at 
    ON users(last_login_at DESC);

-- Index for plan-based queries
CREATE INDEX IF NOT EXISTS idx_users_plan 
    ON users(plan);

-- Index for storage statistics
CREATE INDEX IF NOT EXISTS idx_users_storage_used 
    ON users(storage_used DESC);

-- Composite index for admin user list (plan + created_at)
CREATE INDEX IF NOT EXISTS idx_users_plan_created_at 
    ON users(plan, created_at DESC);

-- Step 7: Add comments for documentation
COMMENT ON COLUMN users.is_blocked IS 'User blocking status (managed by AuthService)';
COMMENT ON COLUMN users.blocked_reason IS 'Reason for blocking';
COMMENT ON COLUMN users.blocked_at IS 'Timestamp when user was blocked';
COMMENT ON COLUMN users.blocked_by IS 'Admin user ID who blocked this user';
COMMENT ON COLUMN users.last_login_at IS 'Last login timestamp (synced from AuthService via Kafka)';
COMMENT ON COLUMN users.plan IS 'User subscription plan: free, premium, business, enterprise';

-- ============================================================
-- Note: user_quotas table is kept for backward compatibility
-- but new code should use users.plan, users.max_file_size, etc.
-- ============================================================

-- ============================================================
-- Verification queries (commented out, for manual testing):
-- ============================================================
-- SELECT id, email, plan, is_blocked, storage_used, storage_quota FROM users LIMIT 5;
-- SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'users';
