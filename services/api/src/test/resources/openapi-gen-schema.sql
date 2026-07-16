-- Spring Modulith event publication registry table for OpenAPI spec generation.
-- This is the minimal schema required for the application to boot via H2
-- without running the full Flyway migration chain.
-- Schema and identifier case mirror Modulith 2.0.6's mysql/mariadb DDL exactly.
CREATE TABLE IF NOT EXISTS EVENT_PUBLICATION (
    ID                     VARCHAR(36)   NOT NULL,
    LISTENER_ID            VARCHAR(512)  NOT NULL,
    EVENT_TYPE             VARCHAR(512)  NOT NULL,
    SERIALIZED_EVENT       VARCHAR(4000) NOT NULL,
    PUBLICATION_DATE       TIMESTAMP(6)  NOT NULL,
    COMPLETION_DATE        TIMESTAMP(6)  DEFAULT NULL,
    STATUS                 VARCHAR(20),
    COMPLETION_ATTEMPTS    INT,
    LAST_RESUBMISSION_DATE TIMESTAMP(6)  DEFAULT NULL,
    PRIMARY KEY (ID)
);

-- Create the index separately for H2 compatibility.
CREATE INDEX IF NOT EXISTS EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX ON EVENT_PUBLICATION(COMPLETION_DATE);
