CREATE TABLE mastodon_app (
    instance VARCHAR(500) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    encrypted_client_secret VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE mastodon_oauth_state (
    state VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    instance VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_mastodon_oauth_state_expires ON mastodon_oauth_state (expires_at);

CREATE TABLE mastodon_connection (
    user_id VARCHAR(255) PRIMARY KEY,
    instance VARCHAR(500) NOT NULL,
    encrypted_access_token VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
