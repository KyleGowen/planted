CREATE TABLE plant_images (
    id                BIGSERIAL PRIMARY KEY,
    plant_id          BIGINT NOT NULL REFERENCES plants (id) ON DELETE CASCADE,
    image_type        TEXT NOT NULL,
    storage_type      TEXT NOT NULL,
    storage_path      TEXT NOT NULL,
    mime_type         TEXT,
    original_filename TEXT,
    captured_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sort_order        INTEGER NOT NULL DEFAULT 0,
    metadata_json     JSONB,

    CONSTRAINT plant_images_image_type_check CHECK (
        image_type IN ('ORIGINAL_UPLOAD', 'HEALTHY_REFERENCE', 'ILLUSTRATED', 'PRUNE_UPDATE')
    ),
    CONSTRAINT plant_images_storage_type_check CHECK (
        storage_type IN ('LOCAL', 'S3', 'URL')
    )
);

CREATE INDEX idx_plant_images_plant_id ON plant_images (plant_id);
CREATE INDEX idx_plant_images_image_type ON plant_images (plant_id, image_type);
