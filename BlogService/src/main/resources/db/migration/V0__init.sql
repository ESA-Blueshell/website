CREATE TABLE blogs
(
    id         BINARY(16)   NOT NULL,
    text       VARCHAR(255) NULL,
    html       VARCHAR(255) NULL,
    created_at datetime NULL,
    deleted_at datetime NULL,
    CONSTRAINT pk_blogs PRIMARY KEY (id)
);