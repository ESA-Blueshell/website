ALTER DATABASE BlogService
    CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

ALTER TABLE blogs
    CONVERT TO CHARACTER SET utf8mb4
        COLLATE utf8mb4_unicode_ci;