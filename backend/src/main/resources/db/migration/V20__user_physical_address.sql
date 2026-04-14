-- One optional home / growing-site address per logical user (for LLM climate context).

CREATE TABLE user_physical_addresses (
    user_id     TEXT PRIMARY KEY,
    address     TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
