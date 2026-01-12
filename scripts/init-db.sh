#!/bin/bash
set -e

# Создаем пользователя (можно в транзакции)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_user WHERE usename = 'filesync') THEN
            CREATE USER filesync WITH PASSWORD 'secret';
        END IF;
    END
    \$\$;
EOSQL

# Создаем базы данных по одной (CREATE DATABASE не может быть в транзакции)
# Каждая команда - отдельный вызов psql с простой командой -c (не heredoc!)
for db in auth_db file_db user_db storage_db sync_db notification_db; do
    if ! psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -tc "SELECT 1 FROM pg_database WHERE datname = '$db'" | grep -q 1; then
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "CREATE DATABASE $db OWNER filesync;"
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -c "GRANT ALL PRIVILEGES ON DATABASE $db TO filesync;"
    fi
done