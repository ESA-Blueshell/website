CREATE TABLE blogs
(
    id         BINARY(16)   NOT NULL,
    title      VARCHAR(255) NULL,
    text       MEDIUMTEXT NULL,
    html       MEDIUMTEXT NULL,
    markdown   MEDIUMTEXT NULL,
    published_at datetime NULL,
    created_at datetime NULL,
    deleted_at datetime NULL,
    CONSTRAINT pk_blogs PRIMARY KEY (id)
);

ALTER TABLE blogs
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;