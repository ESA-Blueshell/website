CREATE TABLE redirects
(
    id           bigint   not null auto_increment,
    telemetry_id bigint NULL,
    created_at   datetime   NULL,
    deleted_at   datetime   NULL,
    CONSTRAINT pk_redirects PRIMARY KEY (id)
);

CREATE TABLE telemetries
(
    id         bigint   not null auto_increment,
    url        VARCHAR(255) NULL,
    platform   SMALLINT     NULL,
    created_at datetime     NULL,
    deleted_at datetime     NULL,
    CONSTRAINT pk_telemetries PRIMARY KEY (id)
);

ALTER TABLE redirects
    ADD CONSTRAINT FK_REDIRECTS_ON_TELEMETRY FOREIGN KEY (telemetry_id) REFERENCES telemetries (id);