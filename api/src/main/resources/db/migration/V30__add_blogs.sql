CREATE TABLE blogs
(
    id           bigint   not null auto_increment,
    title        VARCHAR(255) NULL,
    text         MEDIUMTEXT   NULL,
    html         MEDIUMTEXT   NULL,
    markdown     MEDIUMTEXT   NULL,
    published_at datetime     NULL,
    created_at   datetime     NULL,
    deleted_at   datetime     NULL,
    CONSTRAINT pk_blogs PRIMARY KEY (id)
);