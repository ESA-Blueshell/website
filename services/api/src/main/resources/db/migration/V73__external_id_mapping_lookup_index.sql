-- Prefix index supporting app-level findOwner() conflict detection.
-- external_id is VARCHAR(1024); a full-column utf8mb4 index exceeds the
-- MariaDB key-length limit, so we use the first 191 characters (safe
-- floor for 3-byte utf8mb4 in the default 767-byte key limit).
CREATE INDEX idx_external_id_mapping_lookup
    ON external_id_mapping (aggregate_type, system, external_id(191));
