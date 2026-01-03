CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       name VARCHAR(255),

                       is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
                       blocked_at TIMESTAMP,
                       blocked_reason TEXT,

                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       last_login_at TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
