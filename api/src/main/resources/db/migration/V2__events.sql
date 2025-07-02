ALTER TABLE events
    CHANGE sign_up_form sign_up_form longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE events
    CHANGE description description longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

alter table event_signups
    change options form_answers longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SET NAMES utf8mb4;
ALTER DATABASE blueshell CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci;





