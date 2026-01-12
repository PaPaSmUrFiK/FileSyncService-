-- =========================
-- INITIALIZATION
-- =========================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================
-- NOTIFICATIONS
-- =========================
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'normal',
    data JSONB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    
    CONSTRAINT check_notification_type CHECK (
        notification_type IN (
            'FILE_UPLOADED',
            'FILE_SHARED',
            'FILE_DELETED',
            'SYNC_COMPLETED',
            'SYNC_FAILED',
            'CONFLICT_DETECTED',
            'USER_BLOCKED',
            'USER_UNBLOCKED',
            'QUOTA_CHANGED',
            'PLAN_CHANGED',
            'ROLE_ASSIGNED',
            'ROLE_REVOKED',
            'STORAGE_QUOTA_WARNING',
            'SYSTEM_ANNOUNCEMENT'
        )
    ),
    CONSTRAINT check_priority CHECK (
        priority IN ('low', 'normal', 'high', 'urgent')
    )
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_is_read ON notifications(is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read) WHERE is_read = FALSE;
CREATE INDEX idx_notifications_type ON notifications(notification_type);
CREATE INDEX idx_notifications_priority ON notifications(priority);

-- =========================
-- NOTIFICATION PREFERENCES
-- =========================
CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    websocket_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Настройки по типам уведомлений
    file_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    sync_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    share_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    admin_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    system_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    
    -- Тихие часы
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_preferences_user_id ON notification_preferences(user_id);

-- =========================
-- PUSH TOKENS
-- =========================
CREATE TABLE push_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_id UUID NOT NULL,
    token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    
    UNIQUE(device_id, platform),
    CONSTRAINT check_platform CHECK (platform IN ('fcm', 'apns'))
);

CREATE INDEX idx_push_tokens_user_id ON push_tokens(user_id);
CREATE INDEX idx_push_tokens_device_id ON push_tokens(device_id);
CREATE INDEX idx_push_tokens_is_active ON push_tokens(is_active) WHERE is_active = TRUE;

-- =========================
-- EMAIL QUEUE
-- =========================
CREATE TABLE email_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    to_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    template_name VARCHAR(100),
    template_data JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    
    CONSTRAINT check_status CHECK (
        status IN ('pending', 'sending', 'sent', 'failed')
    )
);

CREATE INDEX idx_email_queue_status ON email_queue(status) WHERE status = 'pending';
CREATE INDEX idx_email_queue_user_id ON email_queue(user_id);
CREATE INDEX idx_email_queue_created_at ON email_queue(created_at);

-- =========================
-- TRIGGERS
-- =========================

-- Триггер для обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_notification_preferences_updated_at
BEFORE UPDATE ON notification_preferences
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
