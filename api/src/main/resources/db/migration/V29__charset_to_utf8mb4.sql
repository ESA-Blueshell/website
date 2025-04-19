ALTER DATABASE API
    CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

ALTER TABLE boards
    CONVERT TO CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

ALTER TABLE board_documents
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE board_members
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE committees
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE committee_members
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE contributions
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE contribution_periods
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE events
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE event_feedback
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE event_pictures
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE event_signups
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE files
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE guests
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE memberships
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE news
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE sponsors
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;

ALTER TABLE users
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;
