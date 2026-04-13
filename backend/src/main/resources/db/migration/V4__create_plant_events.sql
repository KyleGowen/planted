CREATE TABLE plant_watering_events (
    id          BIGSERIAL PRIMARY KEY,
    plant_id    BIGINT NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    watered_at  TIMESTAMPTZ NOT NULL,
    notes       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_watering_events_plant_id ON plant_watering_events (plant_id);
CREATE INDEX idx_watering_events_watered_at ON plant_watering_events (plant_id, watered_at DESC);

CREATE TABLE plant_fertilizer_events (
    id               BIGSERIAL PRIMARY KEY,
    plant_id         BIGINT NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    fertilized_at    TIMESTAMPTZ NOT NULL,
    fertilizer_type  TEXT,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fertilizer_events_plant_id ON plant_fertilizer_events (plant_id);
CREATE INDEX idx_fertilizer_events_fertilized_at ON plant_fertilizer_events (plant_id, fertilized_at DESC);

CREATE TABLE plant_prune_events (
    id          BIGSERIAL PRIMARY KEY,
    plant_id    BIGINT NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    pruned_at   TIMESTAMPTZ NOT NULL,
    notes       TEXT,
    image_id    BIGINT REFERENCES plant_images (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prune_events_plant_id ON plant_prune_events (plant_id);
CREATE INDEX idx_prune_events_pruned_at ON plant_prune_events (plant_id, pruned_at DESC);
