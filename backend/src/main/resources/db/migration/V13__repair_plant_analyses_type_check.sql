-- Dev databases created before INFO_PANEL was allowed will reject INFO_PANEL inserts (HTTP 500 on history summary).
-- Recreate the constraint so analysis_type always matches the JPA enum set.
ALTER TABLE plant_analyses DROP CONSTRAINT IF EXISTS plant_analyses_type_check;

ALTER TABLE plant_analyses ADD CONSTRAINT plant_analyses_type_check CHECK (
    analysis_type IN ('REGISTRATION', 'REANALYSIS', 'PRUNING', 'REMINDER', 'INFO_PANEL')
);
