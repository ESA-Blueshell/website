-- Concern(s): drop obsolete indexes, rename legacy FKs to Spring-style names
-- Order-critical: FOREIGN_KEY_CHECKS disabled during FK churn; helper procedure preserved

SET @OLD_FOREIGN_KEY_CHECKS := @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

DELIMITER $$

/* ---------- Helper: drop index if exists (skips PRIMARY) ---------- */
DROP PROCEDURE IF EXISTS drop_index_if_exists $$
CREATE PROCEDURE drop_index_if_exists(IN p_table VARCHAR(128), IN p_index VARCHAR(128))
BEGIN
    IF p_index <> 'PRIMARY' THEN
        IF EXISTS (SELECT 1
                   FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = p_table
                     AND index_name = p_index) THEN
            SET @sql := CONCAT('ALTER TABLE `', p_table, '` DROP INDEX `', p_index, '`');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;
    END IF;
END $$
DELIMITER ;


/* =========================
   FOREIGN KEY INDEX DROPS
   ========================= */

CALL drop_index_if_exists('boards', 'FK_BOARDS_ON_PICTURE');
CALL drop_index_if_exists('board_documents', 'FK_BOARD_DOCUMENTS_ON_BOARD');
CALL drop_index_if_exists('board_documents', 'FK_BOARD_DOCUMENTS_ON_FILE');
CALL drop_index_if_exists('board_members', 'FK_BOARD_MEMBERS_ON_BOARD');
CALL drop_index_if_exists('board_members', 'FK_BOARD_MEMBERS_ON_PICTURE');
CALL drop_index_if_exists('board_members', 'FK_BOARD_MEMBERS_ON_USER');
CALL drop_index_if_exists('committee_members', 'FK_COMMITTEE_MEMBERS_ON_COMMITTEE');
CALL drop_index_if_exists('contributions', 'FK_CONTRIBUTIONS_ON_MEMBER');
CALL drop_index_if_exists('events', 'fk_events_banner_file');
CALL drop_index_if_exists('event_pictures', 'FK_EVENT_PICTURES_ON_EVENT');
CALL drop_index_if_exists('event_pictures', 'FK_EVENT_PICTURES_ON_PICTURE');
CALL drop_index_if_exists('files', 'FK_FILES_ON_UPLOADER');
CALL drop_index_if_exists('memberships', 'FK_MEMBERSHIPS_ON_SIGNATURE');
CALL drop_index_if_exists('memberships', 'FK_MEMBERSHIPS_ON_USER');
CALL drop_index_if_exists('redirects', 'FK_REDIRECTS_ON_TELEMETRY');


/* =========================
   DROP LEGACY SECONDARY INDEXES
   ========================= */

-- answers
CALL drop_index_if_exists('answers', 'ix_answers_deleted');
CALL drop_index_if_exists('answers', 'ix_answers_qid_deleted');
CALL drop_index_if_exists('answers', 'ix_answers_question_deleted');

-- committee_members

CALL drop_index_if_exists('committee_members', 'user_committee');

-- contributions
CALL drop_index_if_exists('contributions', 'contribution_period_id');

CALL drop_index_if_exists('contributions', 'user_id');

-- events
CALL drop_index_if_exists('events', 'committee_id');
CALL drop_index_if_exists('events', 'creator_id');

CALL drop_index_if_exists('events', 'ix_events_sign_up_form_deleted');
CALL drop_index_if_exists('events', 'last_editor_id');

-- event_banners
CALL drop_index_if_exists('event_banners', 'idx_event_banners_event');
CALL drop_index_if_exists('event_banners', 'idx_event_banners_file');
CALL drop_index_if_exists('event_banners', 'uk_event_file');

-- event_feedback
CALL drop_index_if_exists('event_feedback', 'event_id');

-- event_signups
CALL drop_index_if_exists('event_signups', 'event_signups_event_id_user_id_guest_id_unique');
CALL drop_index_if_exists('event_signups', 'event_signups_guest_id_foreign');
CALL drop_index_if_exists('event_signups', 'event_signups_user_id_foreign');
CALL drop_index_if_exists('event_signups', 'ix_event_signups_event_deleted');

-- event_sign_up_answers
CALL drop_index_if_exists('event_sign_up_answers', 'ix_esa_deleted');
CALL drop_index_if_exists('event_sign_up_answers', 'ix_esa_event_deleted');
CALL drop_index_if_exists('event_sign_up_answers', 'uq_esa_answer');

-- news
CALL drop_index_if_exists('news', 'creator_id');
CALL drop_index_if_exists('news', 'last_editor_id');

-- questions
CALL drop_index_if_exists('questions', 'ix_questions_deleted');
CALL drop_index_if_exists('questions', 'ix_questions_survey_deleted_idx');
CALL drop_index_if_exists('questions', 'ix_questions_survey_deleted_type_count');

-- surveys
CALL drop_index_if_exists('surveys', 'ix_surveys_deleted_at');

-- users
CALL drop_index_if_exists('users', 'uk_users_discord');
CALL drop_index_if_exists('users', 'uk_users_email');
CALL drop_index_if_exists('users', 'uk_users_phone_number');
CALL drop_index_if_exists('users', 'uk_users_username');

/* =========================
   FOREIGN KEY RENAMES
   ========================= */

-- answers
ALTER TABLE `answers`
    DROP FOREIGN KEY `fk_answers_question`,
    ADD CONSTRAINT `fk_answers_question_id`
        FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- authorities
DELETE
FROM authorities
WHERE user_id NOT IN (SELECT id FROM users);

ALTER TABLE `authorities`
    ADD CONSTRAINT `fk_authorities_user_id`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- boards
ALTER TABLE `boards`
    DROP FOREIGN KEY `FK_BOARDS_ON_PICTURE`,
    ADD CONSTRAINT `fk_boards_picture_id`
        FOREIGN KEY (`picture_id`) REFERENCES `files` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- board_documents
ALTER TABLE `board_documents`
    DROP FOREIGN KEY `FK_BOARD_DOCUMENTS_ON_BOARD`,
    ADD CONSTRAINT `fk_board_documents_board_id`
        FOREIGN KEY (`board_id`) REFERENCES `boards` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `board_documents`
    DROP FOREIGN KEY `FK_BOARD_DOCUMENTS_ON_FILE`,
    ADD CONSTRAINT `fk_board_documents_file_id`
        FOREIGN KEY (`file_id`) REFERENCES `files` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- board_members
ALTER TABLE `board_members`
    DROP FOREIGN KEY `FK_BOARD_MEMBERS_ON_BOARD`,
    ADD CONSTRAINT `fk_board_members_board_id`
        FOREIGN KEY (`board_id`) REFERENCES `boards` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `board_members`
    DROP FOREIGN KEY `FK_BOARD_MEMBERS_ON_PICTURE`,
    ADD CONSTRAINT `fk_board_members_picture_id`
        FOREIGN KEY (`picture_id`) REFERENCES `files` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `board_members`
    DROP FOREIGN KEY `FK_BOARD_MEMBERS_ON_USER`,
    ADD CONSTRAINT `fk_board_members_user_id`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- committee_members
ALTER TABLE `committee_members`
    DROP FOREIGN KEY `FK_COMMITTEE_MEMBERS_ON_COMMITTEE`,
    ADD CONSTRAINT `fk_committee_members_committee_id`
        FOREIGN KEY (`committee_id`) REFERENCES `committees` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `committee_members`
    ADD CONSTRAINT `fk_committee_members_user_id`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- contributions
ALTER TABLE `contributions`
    DROP FOREIGN KEY `contributions_ibfk_1`,
    ADD CONSTRAINT `fk_contributions_user_id`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `contributions`
    DROP FOREIGN KEY `contributions_ibfk_2`,
    ADD CONSTRAINT `fk_contributions_contribution_period_id`
        FOREIGN KEY (`contribution_period_id`) REFERENCES `contribution_periods` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `contributions`
    DROP FOREIGN KEY `FK_CONTRIBUTIONS_ON_MEMBER`,
    ADD CONSTRAINT `fk_contributions_member_id`
        FOREIGN KEY (`member_id`) REFERENCES `memberships` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- events
ALTER TABLE `events`
    DROP FOREIGN KEY `events_ibfk_1`,
    ADD CONSTRAINT `fk_events_creator_id`
        FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `events`
    DROP FOREIGN KEY `events_ibfk_2`,
    ADD CONSTRAINT `fk_events_last_editor_id`
        FOREIGN KEY (`last_editor_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `events`
    DROP FOREIGN KEY `events_ibfk_3`,
    ADD CONSTRAINT `fk_events_committee_id`
        FOREIGN KEY (`committee_id`) REFERENCES `committees` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `events`
    DROP FOREIGN KEY `fk_events_survey`,
    ADD CONSTRAINT `fk_events_survey_id`
        FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `events`
    DROP FOREIGN KEY `fk_events_banner_file`,
    ADD CONSTRAINT `fk_events_banner_id`
        FOREIGN KEY (`banner_id`) REFERENCES `event_banners` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- event_banners
ALTER TABLE `event_banners`
    DROP FOREIGN KEY `fk_event_banners_event`,
    ADD CONSTRAINT `fk_event_banners_event_id`
        FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `event_banners`
    DROP FOREIGN KEY `fk_event_banners_file`,
    ADD CONSTRAINT `fk_event_banners_file_id`
        FOREIGN KEY (`file_id`) REFERENCES `files` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- event_feedback
ALTER TABLE `event_feedback`
    DROP FOREIGN KEY `event_feedback_ibfk_1`,
    ADD CONSTRAINT `fk_event_feedback_event_id`
        FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- event_pictures
ALTER TABLE `event_pictures`
    DROP FOREIGN KEY `FK_EVENT_PICTURES_ON_EVENT`,
    ADD CONSTRAINT `fk_event_pictures_event_id`
        FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `event_pictures`
    DROP FOREIGN KEY `FK_EVENT_PICTURES_ON_PICTURE`,
    ADD CONSTRAINT `fk_event_pictures_picture_id`
        FOREIGN KEY (`picture_id`) REFERENCES `files` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- event_signups
ALTER TABLE `event_signups`
    DROP FOREIGN KEY `event_signups_guest_id_foreign`,
    ADD CONSTRAINT `fk_event_signups_guest_id`
        FOREIGN KEY (`guest_id`) REFERENCES `guests` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `event_signups`
    DROP FOREIGN KEY `event_signups_user_id_foreign`,
    ADD CONSTRAINT `fk_event_signups_user_id`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `event_signups`
    DROP FOREIGN KEY `FK_EVENT_SIGNUPS_ON_EVENT`,
    ADD CONSTRAINT `fk_event_signups_event_id`
        FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- event_sign_up_answers
ALTER TABLE `event_sign_up_answers`
    DROP FOREIGN KEY `fk_event_sign_up`,
    ADD CONSTRAINT `fk_event_sign_up_answers_event_sign_up_id`
        FOREIGN KEY (`event_sign_up_id`) REFERENCES `event_signups` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `event_sign_up_answers`
    DROP FOREIGN KEY `fk_event_sign_up_answers_answer`,
    ADD CONSTRAINT `fk_event_sign_up_answers_answer_id`
        FOREIGN KEY (`answer_id`) REFERENCES `answers` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- files
ALTER TABLE `files`
    DROP FOREIGN KEY `FK_FILES_ON_UPLOADER`,
    ADD CONSTRAINT `fk_files_uploader_id`
        FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- memberships
ALTER TABLE `memberships`
    DROP FOREIGN KEY `FK_MEMBERSHIPS_ON_SIGNATURE`,
    ADD CONSTRAINT `fk_memberships_signature_id`
        FOREIGN KEY (`signature_id`) REFERENCES `files` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `memberships`
    DROP FOREIGN KEY `FK_MEMBERSHIPS_ON_USER`,
    ADD CONSTRAINT `fk_memberships_user_id`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- news (assumed creator_id / last_editor_id -> users.id)
ALTER TABLE `news`
    DROP FOREIGN KEY `news_ibfk_1`,
    ADD CONSTRAINT `fk_news_creator_id`
        FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `news`
    DROP FOREIGN KEY `news_ibfk_2`,
    ADD CONSTRAINT `fk_news_last_editor_id`
        FOREIGN KEY (`last_editor_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- questions
ALTER TABLE `questions`
    DROP FOREIGN KEY `fk_questions_survey`,
    ADD CONSTRAINT `fk_questions_survey_id`
        FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- redirects
ALTER TABLE `redirects`
    DROP FOREIGN KEY `FK_REDIRECTS_ON_TELEMETRY`,
    ADD CONSTRAINT `fk_redirects_telemetry_id`
        FOREIGN KEY (`telemetry_id`) REFERENCES `telemetries` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- sponsors
ALTER TABLE `sponsors`
    DROP FOREIGN KEY `FK_SPONSORS_ON_LOGO`,
    ADD CONSTRAINT `fk_sponsors_logo_id`
        FOREIGN KEY (`logo_id`) REFERENCES `files` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

-- users
ALTER TABLE `users`
    DROP COLUMN `registration_id`;

ALTER TABLE `users`
    DROP FOREIGN KEY `fk_users_address`,
    ADD CONSTRAINT `fk_users_address_id`
        FOREIGN KEY (`address_id`) REFERENCES `addresses` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `users`
    DROP FOREIGN KEY `FK_USERS_ON_CREATOR`,
    ADD CONSTRAINT `fk_users_creator_id`
        FOREIGN KEY (`creator_id`) REFERENCES `users` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE `users`
    CHANGE COLUMN `profile_picture` `profile_picture_id` BIGINT;

ALTER TABLE `users`
    DROP FOREIGN KEY `FK_USERS_ON_PROFILE_PICTURE`,
    ADD CONSTRAINT `fk_users_profile_picture_id`
        FOREIGN KEY (`profile_picture_id`) REFERENCES `files` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT;

/* ---------- Cleanup helper ---------- */
DROP PROCEDURE drop_index_if_exists;

SET FOREIGN_KEY_CHECKS = @OLD_FOREIGN_KEY_CHECKS;
