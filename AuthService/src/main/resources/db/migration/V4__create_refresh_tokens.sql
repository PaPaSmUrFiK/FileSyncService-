CREATE TABLE refresh_tokens (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                                user_id UUID NOT NULL,
                                token_hash VARCHAR(255) NOT NULL UNIQUE,

                                device_info TEXT,

                                expires_at TIMESTAMP NOT NULL,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                revoked BOOLEAN NOT NULL DEFAULT FALSE,
                                revoked_at TIMESTAMP,

                                CONSTRAINT fk_refresh_tokens_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users(id)
                                        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX idx_refresh_tokens_token_hash
    ON refresh_tokens(token_hash);
