-- Основная таблица пользователей (синхронизирована с AuthService)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    avatar_url VARCHAR(500),
    storage_used BIGINT NOT NULL DEFAULT 0,
    storage_quota BIGINT NOT NULL DEFAULT 5368709120, -- 5GB
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_storage_positive CHECK (storage_used >= 0),
    CONSTRAINT check_storage_quota CHECK (storage_quota > 0)
);

-- Настройки пользователя
CREATE TABLE user_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    theme VARCHAR(20) NOT NULL DEFAULT 'light',
    language VARCHAR(10) NOT NULL DEFAULT 'en',
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    auto_sync BOOLEAN NOT NULL DEFAULT TRUE,
    sync_on_mobile_data BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_theme CHECK (theme IN ('light', 'dark', 'system'))
);

-- Тарифные планы пользователей
CREATE TABLE user_quotas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_type VARCHAR(50) NOT NULL DEFAULT 'free',
    max_file_size BIGINT NOT NULL DEFAULT 104857600, -- 100MB
    max_devices INTEGER NOT NULL DEFAULT 3,
    max_shares INTEGER NOT NULL DEFAULT 10,
    version_history_days INTEGER NOT NULL DEFAULT 30,
    valid_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_plan_type CHECK (plan_type IN ('free', 'premium', 'business', 'enterprise'))
);

-- История административных действий
CREATE TABLE admin_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID NOT NULL REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    target_user_id UUID REFERENCES users(id),
    action_details JSONB,
    ip_address INET,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Индексы
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_user_settings_user_id ON user_settings(user_id);
CREATE INDEX idx_user_quotas_user_id ON user_quotas(user_id);
CREATE INDEX idx_user_quotas_plan_type ON user_quotas(plan_type);
CREATE INDEX idx_admin_actions_admin_id ON admin_actions(admin_id);
CREATE INDEX idx_admin_actions_target_user ON admin_actions(target_user_id);
CREATE INDEX idx_admin_actions_created_at ON admin_actions(created_at);

-- Триггер для обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS '
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
' LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_settings_updated_at
BEFORE UPDATE ON user_settings
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_quotas_updated_at
BEFORE UPDATE ON user_quotas
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Триггер для автоматического создания настроек и квот при создании пользователя
CREATE OR REPLACE FUNCTION create_user_defaults()
RETURNS TRIGGER AS '
BEGIN
    -- Создаем настройки по умолчанию
    INSERT INTO user_settings (user_id)
    VALUES (NEW.id);
    
    -- Создаем квоты по умолчанию
    INSERT INTO user_quotas (user_id)
    VALUES (NEW.id);
    
    RETURN NEW;
END;
' LANGUAGE plpgsql;

CREATE TRIGGER after_user_insert
AFTER INSERT ON users
FOR EACH ROW
EXECUTE FUNCTION create_user_defaults();
