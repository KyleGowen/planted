CREATE TABLE plant_reminder_state (
    id                          BIGSERIAL PRIMARY KEY,
    plant_id                    BIGINT NOT NULL UNIQUE REFERENCES plants (id) ON DELETE CASCADE,
    watering_due                BOOLEAN NOT NULL DEFAULT FALSE,
    watering_overdue            BOOLEAN NOT NULL DEFAULT FALSE,
    fertilizer_due              BOOLEAN NOT NULL DEFAULT FALSE,
    pruning_due                 BOOLEAN NOT NULL DEFAULT FALSE,
    health_attention_needed     BOOLEAN NOT NULL DEFAULT FALSE,
    goal_attention_needed       BOOLEAN NOT NULL DEFAULT FALSE,
    next_watering_instruction   TEXT,
    next_fertilizer_instruction TEXT,
    next_pruning_instruction    TEXT,
    last_computed_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reminder_state_plant_id ON plant_reminder_state (plant_id);
