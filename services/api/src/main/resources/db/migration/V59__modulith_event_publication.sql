-- Spring Modulith event publication registry table.
-- Schema and identifier case mirror Modulith 2.0.6's mysql/mariadb DDL exactly
-- (CREATE TABLE IF NOT EXISTS EVENT_PUBLICATION) so Modulith's idempotent init
-- becomes a no-op and reads / writes hit the same table on case-sensitive
-- filesystems.
CREATE TABLE EVENT_PUBLICATION (
    ID                     VARCHAR(36)   NOT NULL,
    LISTENER_ID            VARCHAR(512)  NOT NULL,
    EVENT_TYPE             VARCHAR(512)  NOT NULL,
    SERIALIZED_EVENT       VARCHAR(4000) NOT NULL,
    PUBLICATION_DATE       TIMESTAMP(6)  NOT NULL,
    COMPLETION_DATE        TIMESTAMP(6)  DEFAULT NULL,
    STATUS                 VARCHAR(20),
    COMPLETION_ATTEMPTS    INT,
    LAST_RESUBMISSION_DATE TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (ID),
    INDEX EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX (COMPLETION_DATE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
