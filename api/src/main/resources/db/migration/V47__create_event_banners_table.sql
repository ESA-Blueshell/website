CREATE TABLE IF NOT EXISTS event_banners
(
    id         BIGINT NOT NULL AUTO_INCREMENT,
    event_id   BIGINT     NOT NULL,
    file_id    BIGINT     NOT NULL,
    deleted_at DATETIME   NOT NULL DEFAULT '9999-12-31 23:59:59',
    CONSTRAINT pk_event_banners PRIMARY KEY (id),
    CONSTRAINT fk_event_banners_event
        FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_event_banners_file
        FOREIGN KEY (file_id) REFERENCES files (id),
    CONSTRAINT uk_event_file UNIQUE (event_id, file_id, deleted_at)
);

CREATE INDEX idx_event_banners_event ON event_banners (event_id);
CREATE INDEX idx_event_banners_file ON event_banners (file_id);

INSERT INTO event_banners (file_id, event_id, deleted_at)
SELECT events.banner_id as file_id, events.id as event_id, files.deleted_at as deleted_at
FROM events,
     files
WHERE events.banner_id = files.id;