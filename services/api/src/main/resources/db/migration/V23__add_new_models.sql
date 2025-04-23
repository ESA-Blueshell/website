CREATE TABLE board_documents
(
    id       BIGINT AUTO_INCREMENT NOT NULL,
    board_id BIGINT                NULL,
    name     VARCHAR(255)          NULL,
    file_id  BIGINT                NULL,
    deleted_at  datetime              NULL,
    CONSTRAINT pk_board_documents PRIMARY KEY (id)
);

CREATE TABLE board_members
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    board_id   BIGINT                NULL,
    user_id    BIGINT                NULL,
    picture_id BIGINT                NULL,
    deleted_at  datetime              NULL,
    CONSTRAINT pk_board_members PRIMARY KEY (id)
);

CREATE TABLE boards
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    name       VARCHAR(255)          NULL,
    picture_id BIGINT                NULL,
    candidate  VARCHAR(255)          NULL,
    start_date date                  NULL,
    end_date   date                  NULL,
    deleted_at  datetime              NULL,
    CONSTRAINT pk_boards PRIMARY KEY (id)
);

CREATE TABLE event_pictures
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    picture_id BIGINT                NULL,
    event_id   BIGINT                NULL,
    deleted_at datetime              NULL,
    CONSTRAINT pk_event_pictures PRIMARY KEY (id)
);

CREATE TABLE files
(
    id          BIGINT AUTO_INCREMENT NOT NULL,
    name        VARCHAR(255)          NULL,
    url         VARCHAR(255)          NULL,
    uploader_id BIGINT                NULL,
    created_at  datetime              NULL,
    media_type  VARCHAR(255)          NULL,
    size        BIGINT                NULL,
    type        VARCHAR(255)          NULL,
    deleted_at  datetime              NULL,
    CONSTRAINT pk_files PRIMARY KEY (id)
);

CREATE TABLE memberships
(
    id           BIGINT AUTO_INCREMENT NOT NULL,
    user_id      BIGINT                NULL,
    start_date   date                  NULL,
    end_date     date                  NULL,
    type         VARCHAR(2555)         NULL,
    city         VARCHAR(255)          NULL,
    incasso      tinyint(1)            NULL,
    signature_id BIGINT                NULL,
    deleted_at  datetime              NULL,
    CONSTRAINT pk_memberships PRIMARY KEY (id)
);

ALTER TABLE boards
    ADD CONSTRAINT FK_BOARDS_ON_PICTURE FOREIGN KEY (picture_id) REFERENCES files (id);

ALTER TABLE board_documents
    ADD CONSTRAINT FK_BOARD_DOCUMENTS_ON_BOARD FOREIGN KEY (board_id) REFERENCES boards (id);

ALTER TABLE board_documents
    ADD CONSTRAINT FK_BOARD_DOCUMENTS_ON_FILE FOREIGN KEY (file_id) REFERENCES files (id);

ALTER TABLE board_members
    ADD CONSTRAINT FK_BOARD_MEMBERS_ON_BOARD FOREIGN KEY (board_id) REFERENCES boards (id);

ALTER TABLE board_members
    ADD CONSTRAINT FK_BOARD_MEMBERS_ON_PICTURE FOREIGN KEY (picture_id) REFERENCES files (id);

ALTER TABLE board_members
    ADD CONSTRAINT FK_BOARD_MEMBERS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE committee_members
    ADD CONSTRAINT FK_COMMITTEE_MEMBERS_ON_COMMITTEE FOREIGN KEY (committee_id) REFERENCES committees (id);

ALTER TABLE contributions
    ADD COLUMN member_id BIGINT;

ALTER TABLE contributions
    ADD CONSTRAINT FK_CONTRIBUTIONS_ON_MEMBER FOREIGN KEY (member_id) REFERENCES memberships (id);

ALTER TABLE event_pictures
    ADD CONSTRAINT FK_EVENT_PICTURES_ON_EVENT FOREIGN KEY (event_id) REFERENCES events (id);

ALTER TABLE event_pictures
    ADD CONSTRAINT FK_EVENT_PICTURES_ON_PICTURE FOREIGN KEY (picture_id) REFERENCES files (id);

ALTER TABLE event_signups
    ADD CONSTRAINT FK_EVENT_SIGNUPS_ON_EVENT FOREIGN KEY (event_id) REFERENCES events (id);

ALTER TABLE files
    ADD CONSTRAINT FK_FILES_ON_UPLOADER FOREIGN KEY (uploader_id) REFERENCES users (id);

ALTER TABLE memberships
    ADD CONSTRAINT FK_MEMBERSHIPS_ON_SIGNATURE FOREIGN KEY (signature_id) REFERENCES files (id);

ALTER TABLE memberships
    ADD CONSTRAINT FK_MEMBERSHIPS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE sponsors
    ADD CONSTRAINT FK_SPONSORS_ON_LOGO FOREIGN KEY (logo_id) REFERENCES files (id);

ALTER TABLE users
    ADD COLUMN creator_id BIGINT;

ALTER TABLE users
    ADD CONSTRAINT FK_USERS_ON_CREATOR FOREIGN KEY (creator_id) REFERENCES users (id);

ALTER TABLE users
    ADD CONSTRAINT FK_USERS_ON_PROFILE_PICTURE FOREIGN KEY (profile_picture) REFERENCES files (id);