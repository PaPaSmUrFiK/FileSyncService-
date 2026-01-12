-- Исправляем триггер создания дефолтных настроек и квот
-- Используем $$ для тела функции и добавляем ON CONFLICT для стабильности

CREATE OR REPLACE FUNCTION create_user_defaults()
RETURNS TRIGGER AS $$
BEGIN
    -- Создаем настройки по умолчанию, если их еще нет
    INSERT INTO user_settings (user_id, theme, language)
    VALUES (NEW.id, 'light', 'en')
    ON CONFLICT (user_id) DO NOTHING;
    
    -- Создаем квоты по умолчанию (план 'free' в нижнем регистре для соответствия CONSTRAINT)
    INSERT INTO user_quotas (user_id, plan_type, max_file_size, max_devices, max_shares, version_history_days)
    VALUES (NEW.id, 'free', 104857600, 3, 10, 30)
    ON CONFLICT (user_id) DO NOTHING;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;








