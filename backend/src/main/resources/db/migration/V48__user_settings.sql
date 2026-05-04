CREATE TABLE user_settings (
    user_id                            VARCHAR(255) PRIMARY KEY,
    display_name                       VARCHAR(100),
    openai_api_key_override            TEXT,
    screensaver_slide_duration_seconds INTEGER NOT NULL DEFAULT 60,
    updated_at                         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
