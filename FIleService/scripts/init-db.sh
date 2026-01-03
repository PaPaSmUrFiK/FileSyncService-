#!/bin/bash
set -e

echo "Initializing database..."

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Create role if it doesn't exist
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'filesync') THEN
            CREATE ROLE filesync WITH LOGIN PASSWORD 'secret';
        END IF;
    END
    \$\$;

    -- Create database if it doesn't exist
    SELECT 'CREATE DATABASE file_db OWNER filesync'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'file_db')\gexec

    -- Grant privileges
    GRANT ALL PRIVILEGES ON DATABASE file_db TO filesync;
EOSQL

echo "PostgreSQL initialization completed successfully."