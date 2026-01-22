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
        'FILE_PERMANENTLY_DELETED',
        'FILE_RESTORED',
        'FILE_UNSHARED',
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
