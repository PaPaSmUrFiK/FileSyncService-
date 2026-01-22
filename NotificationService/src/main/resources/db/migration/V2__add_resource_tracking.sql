-- Add resource tracking fields to notifications table
ALTER TABLE notifications
ADD COLUMN IF NOT EXISTS resource_id UUID,
ADD COLUMN IF NOT EXISTS resource_type VARCHAR(50);

-- Update notification types to include new events
ALTER TABLE notifications
DROP CONSTRAINT IF EXISTS check_notification_type;

ALTER TABLE notifications
ADD CONSTRAINT check_notification_type CHECK (
    notification_type IN (
        'FILE_CREATED',
        'FILE_UPLOADED',
        'FILE_RENAMED',
        'FILE_VERSION_UPLOADED',
        'FILE_SHARED',
        'FILE_DELETED',
        'USER_BLOCKED',
        'USER_UNBLOCKED',
        'USER_ROLE_CHANGED',
        'USER_PASSWORD_CHANGED',
        'SYNC_COMPLETED',
        'SYNC_FAILED',
        'CONFLICT_DETECTED',
        'QUOTA_CHANGED',
        'PLAN_CHANGED',
        'STORAGE_QUOTA_WARNING',
        'SYSTEM_ANNOUNCEMENT'
    )
);

-- Add index for resource lookups
CREATE INDEX IF NOT EXISTS idx_notifications_resource ON notifications(resource_id, resource_type);
