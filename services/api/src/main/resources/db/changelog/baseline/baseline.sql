--liquibase formatted sql logicalFilePath:db/changelog/baseline/baseline.sql

--changeset baseline:schema splitStatements:true
--comment The schema as production has it. Applied on an empty database; marked already-applied on production itself.

-- Tables come out alphabetically, so a foreign key routinely names a table
-- further down the file. Checks go back on at the end of the changeset.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `EVENT_PUBLICATION` (
  `ID` varchar(36) NOT NULL,
  `LISTENER_ID` varchar(512) NOT NULL,
  `EVENT_TYPE` varchar(512) NOT NULL,
  `SERIALIZED_EVENT` varchar(4000) NOT NULL,
  `PUBLICATION_DATE` timestamp(6) NOT NULL,
  `COMPLETION_DATE` timestamp(6) NULL DEFAULT NULL,
  `STATUS` varchar(20) DEFAULT NULL,
  `COMPLETION_ATTEMPTS` int(11) DEFAULT NULL,
  `LAST_RESUBMISSION_DATE` timestamp(6) NULL DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `EVENT_PUBLICATION_BY_COMPLETION_DATE_IDX` (`COMPLETION_DATE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `addresses` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `house_number` mediumtext DEFAULT NULL,
  `zip_code` mediumtext DEFAULT NULL,
  `city` mediumtext DEFAULT NULL,
  `street` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_addresses_city` (`city`(768)),
  KEY `idx_addresses_zip_code` (`zip_code`(768)),
  KEY `idx_addresses_deleted_at` (`deleted_at`),
  KEY `fk_addresses_on_created_by` (`created_by_id`),
  KEY `fk_addresses_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_addresses_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_addresses_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=153 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `answers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `question_id` bigint(20) NOT NULL,
  `option_selections` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`option_selections`)),
  `text_response` text DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_answers_question_id` (`question_id`),
  KEY `idx_answers_deleted_at` (`deleted_at`),
  KEY `fk_answers_on_created_by` (`created_by_id`),
  KEY `fk_answers_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_answers_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_answers_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_answers_question_id` FOREIGN KEY (`question_id`) REFERENCES `questions` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1371 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `authorities` (
  `user_id` bigint(20) NOT NULL,
  `authority` varchar(255) NOT NULL,
  PRIMARY KEY (`user_id`,`authority`),
  CONSTRAINT `fk_authorities_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;
CREATE TABLE `blogs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `html` longtext NOT NULL,
  `published_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blogs_title_deleted_at` (`title`,`deleted_at`),
  KEY `idx_blogs_created_at` (`created_at`),
  KEY `idx_blogs_published_at` (`published_at`),
  KEY `idx_blogs_title` (`title`),
  KEY `idx_blogs_deleted_at` (`deleted_at`),
  KEY `fk_blogs_on_created_by` (`created_by_id`),
  KEY `fk_blogs_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_blogs_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_blogs_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `board_documents` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `board_id` bigint(20) NOT NULL,
  `name` varchar(255) NOT NULL,
  `file_id` bigint(20) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_board_documents_board_name_deleted_at` (`board_id`,`name`,`deleted_at`),
  UNIQUE KEY `uk_board_documents_file_deleted_at` (`file_id`,`deleted_at`),
  KEY `idx_board_documents_board_id` (`board_id`),
  KEY `idx_board_documents_deleted_at` (`deleted_at`),
  KEY `idx_board_documents_file_id` (`file_id`),
  KEY `fk_board_documents_on_created_by` (`created_by_id`),
  KEY `fk_board_documents_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_board_documents_board_id` FOREIGN KEY (`board_id`) REFERENCES `boards` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_board_documents_file_id` FOREIGN KEY (`file_id`) REFERENCES `files` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_board_documents_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_board_documents_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `board_members` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `board_id` bigint(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `display_name` varchar(128) DEFAULT NULL,
  `nickname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `picture_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `role` varchar(255) NOT NULL DEFAULT 'Member',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_board_members_board_user_deleted_at` (`board_id`,`user_id`,`deleted_at`),
  UNIQUE KEY `uk_board_members_picture_deleted_at` (`picture_id`,`deleted_at`),
  KEY `fk_board_members_user_id` (`user_id`),
  KEY `idx_board_members_board_id` (`board_id`),
  KEY `idx_board_members_deleted_at` (`deleted_at`),
  KEY `fk_board_members_on_created_by` (`created_by_id`),
  KEY `fk_board_members_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_board_members_board_id` FOREIGN KEY (`board_id`) REFERENCES `boards` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_board_members_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_board_members_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_board_members_picture_id` FOREIGN KEY (`picture_id`) REFERENCES `files` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_board_members_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `boards` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `number` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `cheer` varchar(255) DEFAULT NULL,
  `accent` varchar(32) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `picture_id` bigint(20) DEFAULT NULL,
  `image` varchar(255) DEFAULT NULL,
  `candidate` varchar(255) NOT NULL,
  `start_date` datetime NOT NULL,
  `end_date` date DEFAULT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_boards_number_deleted_at` (`number`,`deleted_at`),
  UNIQUE KEY `uk_boards_picture_deleted_at` (`picture_id`,`deleted_at`),
  KEY `idx_boards_end_date` (`end_date`),
  KEY `idx_boards_name` (`name`),
  KEY `idx_boards_start_date` (`start_date`),
  KEY `idx_boards_deleted_at` (`deleted_at`),
  KEY `fk_boards_on_created_by` (`created_by_id`),
  KEY `fk_boards_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_boards_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_boards_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_boards_picture_id` FOREIGN KEY (`picture_id`) REFERENCES `files` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `cohort` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `system` varchar(32) NOT NULL,
  `kind` varchar(32) NOT NULL,
  `label` varchar(255) NOT NULL,
  `folder` varchar(64) DEFAULT NULL,
  `subject_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  `external_id` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cohort_system_kind` (`system`,`kind`,`deleted_at`),
  KEY `idx_cohort_deleted_at` (`deleted_at`),
  KEY `fk_cohort_created` (`created_by_id`),
  KEY `fk_cohort_updated` (`updated_by_id`),
  KEY `idx_cohort_folder` (`folder`),
  KEY `idx_cohort_subject_fk` (`subject_id`),
  KEY `idx_cohort_external_id` (`system`,`external_id`(191),`deleted_at`),
  CONSTRAINT `fk_cohort_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_cohort_subject` FOREIGN KEY (`subject_id`) REFERENCES `cohort_subject` (`id`),
  CONSTRAINT `fk_cohort_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `cohort_member` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cohort_id` bigint(20) NOT NULL,
  `subject_id` bigint(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `external_user_id` varchar(255) DEFAULT NULL,
  `synced_at` datetime(6) DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `label` varchar(512) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cohort_member` (`cohort_id`,`user_id`,`deleted_at`),
  UNIQUE KEY `uk_cohort_member_external` (`cohort_id`,`external_user_id`,`deleted_at`),
  KEY `idx_cohort_member_cohort` (`cohort_id`),
  KEY `idx_cohort_member_user` (`user_id`),
  KEY `idx_cohort_member_deleted_at` (`deleted_at`),
  KEY `fk_cohort_member_created` (`created_by_id`),
  KEY `fk_cohort_member_updated` (`updated_by_id`),
  KEY `idx_cohort_member_subject` (`subject_id`),
  KEY `idx_cohort_member_external` (`cohort_id`,`external_user_id`),
  KEY `idx_cohort_member_verified` (`cohort_id`,`verified_at`),
  KEY `idx_cohort_member_synced` (`cohort_id`,`synced_at`),
  CONSTRAINT `fk_cohort_member_cohort` FOREIGN KEY (`cohort_id`) REFERENCES `cohort` (`id`),
  CONSTRAINT `fk_cohort_member_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_cohort_member_subject` FOREIGN KEY (`subject_id`) REFERENCES `cohort_subject` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cohort_member_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_cohort_member_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1010 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `cohort_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `fact_kind` varchar(32) NOT NULL,
  `fact_key` varchar(64) NOT NULL,
  `cohort_id` bigint(20) NOT NULL,
  `subject_id` bigint(20) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cohort_rule` (`fact_kind`,`fact_key`,`cohort_id`),
  KEY `idx_cohort_rule_fact_enabled` (`fact_kind`,`fact_key`,`enabled`),
  KEY `idx_cohort_rule_cohort` (`cohort_id`),
  KEY `idx_cohort_rule_subject` (`subject_id`),
  CONSTRAINT `fk_cohort_rule_cohort` FOREIGN KEY (`cohort_id`) REFERENCES `cohort` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cohort_rule_subject` FOREIGN KEY (`subject_id`) REFERENCES `cohort_subject` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `cohort_subject` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `type` varchar(32) NOT NULL,
  `definition_key` varchar(64) DEFAULT NULL,
  `label` varchar(255) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cohort_subject_definition` (`definition_key`,`deleted_at`),
  KEY `idx_cohort_subject_type` (`type`,`deleted_at`),
  KEY `idx_cohort_subject_deleted_at` (`deleted_at`),
  KEY `fk_cohort_subject_created` (`created_by_id`),
  KEY `fk_cohort_subject_updated` (`updated_by_id`),
  CONSTRAINT `fk_cohort_subject_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_cohort_subject_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `committee_members` (
  `user_id` bigint(20) NOT NULL,
  `committee_id` bigint(20) NOT NULL,
  `role` varchar(255) DEFAULT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_committee_members_committee_user_deleted_at` (`committee_id`,`user_id`,`deleted_at`),
  KEY `fk_committee_members_user_id` (`user_id`),
  KEY `idx_committee_members_committee_role` (`committee_id`,`role`),
  KEY `idx_committee_members_committee_id` (`committee_id`),
  KEY `idx_committee_members_deleted_at` (`deleted_at`),
  KEY `fk_committee_members_on_created_by` (`created_by_id`),
  KEY `fk_committee_members_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_committee_members_committee_id` FOREIGN KEY (`committee_id`) REFERENCES `committees` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_committee_members_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_committee_members_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_committee_members_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=167 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `committees` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `description` varchar(4095) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_committees_name_deleted_at` (`name`,`deleted_at`),
  KEY `idx_committees_name` (`name`),
  KEY `idx_committees_deleted_at` (`deleted_at`),
  KEY `fk_committees_on_created_by` (`created_by_id`),
  KEY `fk_committees_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_committees_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_committees_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `contact_external_ids` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `contact_id` bigint(20) NOT NULL,
  `system` varchar(50) NOT NULL,
  `external_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contact_external_ids_contact_system` (`contact_id`,`system`),
  CONSTRAINT `fk_contact_external_ids_contact` FOREIGN KEY (`contact_id`) REFERENCES `contacts` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=521 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `contact_list_external_ids` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `contact_list_id` bigint(20) NOT NULL,
  `system` varchar(50) NOT NULL,
  `external_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contact_list_external_ids_list_system` (`contact_list_id`,`system`),
  CONSTRAINT `fk_contact_list_external_ids_list` FOREIGN KEY (`contact_list_id`) REFERENCES `contact_lists` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Retained snapshot for cohort-cutover rollback. Backfilled to external_id_mapping (aggregate_type=COHORT); drop in follow-up migration once verified.';
CREATE TABLE `contact_list_memberships` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `contact_id` bigint(20) NOT NULL,
  `contact_list_id` bigint(20) NOT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contact_list_memberships` (`contact_id`,`contact_list_id`,`deleted_at`),
  KEY `idx_contact_list_memberships_contact` (`contact_id`),
  KEY `idx_contact_list_memberships_list` (`contact_list_id`),
  KEY `idx_contact_list_memberships_deleted` (`deleted_at`),
  KEY `fk_membership_created` (`created_by_id`),
  KEY `fk_membership_updated` (`updated_by_id`),
  CONSTRAINT `fk_membership_contact` FOREIGN KEY (`contact_id`) REFERENCES `contacts` (`id`),
  CONSTRAINT `fk_membership_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_membership_list` FOREIGN KEY (`contact_list_id`) REFERENCES `contact_lists` (`id`),
  CONSTRAINT `fk_membership_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=216 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Retained snapshot for cohort-cutover rollback. Backfilled to cohort_member table; drop in follow-up migration once verified.';
CREATE TABLE `contact_lists` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `folder_name` varchar(100) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contact_lists_name_deleted_at` (`name`,`deleted_at`),
  KEY `idx_contact_lists_deleted_at` (`deleted_at`),
  KEY `fk_contact_lists_created` (`created_by_id`),
  KEY `fk_contact_lists_updated` (`updated_by_id`),
  CONSTRAINT `fk_contact_lists_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_contact_lists_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Retained snapshot for cohort-cutover rollback. Backfilled to cohort table; drop in follow-up migration once verified.';
CREATE TABLE `contacts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `synced_email` varchar(255) NOT NULL DEFAULT '',
  `synced_first_name` varchar(255) NOT NULL DEFAULT '',
  `synced_last_name` varchar(255) NOT NULL DEFAULT '',
  `synced_phone_number` varchar(50) DEFAULT NULL,
  `synced_newsletter` tinyint(1) NOT NULL DEFAULT 0,
  `synced_is_member` tinyint(1) NOT NULL DEFAULT 0,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contacts_user_id_deleted_at` (`user_id`,`deleted_at`),
  KEY `idx_contacts_user_id` (`user_id`),
  KEY `idx_contacts_deleted_at` (`deleted_at`),
  KEY `fk_contacts_created` (`created_by_id`),
  KEY `fk_contacts_updated` (`updated_by_id`),
  CONSTRAINT `fk_contacts_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_contacts_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_contacts_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=433 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `contribution_periods` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  `half_year_cutoff_date` date NOT NULL,
  `half_year_fee` decimal(10,2) NOT NULL,
  `full_year_fee` decimal(10,2) NOT NULL,
  `alumni_fee` decimal(10,2) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  `contact_list_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contribution_periods_start_end_deleted_at` (`start_date`,`end_date`,`deleted_at`),
  KEY `idx_contribution_periods_end_date` (`end_date`),
  KEY `idx_contribution_periods_start_date` (`start_date`),
  KEY `idx_contribution_periods_deleted_at` (`deleted_at`),
  KEY `fk_contribution_periods_on_created_by` (`created_by_id`),
  KEY `fk_contribution_periods_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_contribution_periods_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_contribution_periods_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `contribution_reminders` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `user_id` bigint(20) NOT NULL,
  `contribution_period_id` bigint(20) NOT NULL,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  `fee_type` varchar(32) DEFAULT NULL,
  `amount` double DEFAULT NULL,
  `payment_due_date` date DEFAULT NULL,
  `asked_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_contribution_reminders_deleted_at` (`deleted_at`),
  KEY `idx_contribution_reminders_contribution_period_id` (`contribution_period_id`,`deleted_at`),
  KEY `idx_contribution_reminders_user_id` (`user_id`,`deleted_at`),
  KEY `fk_contribution_reminders_on_created_by` (`created_by_id`),
  KEY `fk_contribution_reminders_on_updated_by` (`updated_by_id`),
  KEY `idx_contribution_reminders_created_at` (`created_at`),
  KEY `idx_contribution_reminders_user_period_asked` (`user_id`,`contribution_period_id`,`asked_at`),
  CONSTRAINT `fk_contribution_reminders_contribution_period_id` FOREIGN KEY (`contribution_period_id`) REFERENCES `contribution_periods` (`id`),
  CONSTRAINT `fk_contribution_reminders_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_contribution_reminders_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_contribution_reminders_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `contributions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `contribution_period_id` bigint(20) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contributions_user_period_deleted_at` (`user_id`,`contribution_period_id`,`deleted_at`),
  KEY `fk_contributions_contribution_period_id` (`contribution_period_id`),
  KEY `idx_contributions_deleted_at` (`deleted_at`),
  KEY `idx_contributions_user_id` (`user_id`),
  KEY `idx_contributions_created_at` (`created_at`),
  KEY `fk_contributions_on_created_by` (`created_by_id`),
  KEY `fk_contributions_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_contributions_contribution_period_id` FOREIGN KEY (`contribution_period_id`) REFERENCES `contribution_periods` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_contributions_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_contributions_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_contributions_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=505 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `deleted_users` (
  `user_id` bigint(20) NOT NULL,
  `username` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `initials` varchar(255) NOT NULL,
  `first_name` varchar(255) NOT NULL,
  `prefix` varchar(255) DEFAULT NULL,
  `last_name` varchar(255) NOT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `discord` varchar(255) DEFAULT NULL,
  `newsletter` bit(1) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `address_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime NOT NULL,
  `restore_until_at` datetime NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `photo_consent` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`),
  KEY `idx_deleted_users_restore_until_at` (`restore_until_at`),
  KEY `idx_deleted_users_deleted_at` (`deleted_at`),
  KEY `idx_deleted_users_address_id` (`address_id`),
  CONSTRAINT `fk_deleted_users_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `emails` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `recipient_email` varchar(255) NOT NULL,
  `recipient_name` varchar(255) NOT NULL,
  `subject` varchar(512) NOT NULL,
  `email_type` varchar(255) NOT NULL,
  `delivery_status` varchar(32) NOT NULL DEFAULT 'PENDING',
  `message_id` varchar(255) DEFAULT NULL,
  `tracking_token` varchar(36) DEFAULT NULL,
  `sent_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `opened_at` datetime DEFAULT NULL,
  `error_type` varchar(255) DEFAULT NULL,
  `error_reason` longtext DEFAULT NULL,
  `attempts` int(11) NOT NULL DEFAULT 0,
  `job_execution_id` bigint(20) DEFAULT NULL,
  `initiated_by_user_id` bigint(20) DEFAULT NULL,
  `initiated_by_type` varchar(16) NOT NULL DEFAULT 'SYSTEM',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `body_markdown` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_emails_tracking_token` (`tracking_token`),
  KEY `idx_emails_status` (`delivery_status`),
  KEY `idx_emails_email_type` (`email_type`),
  KEY `idx_emails_recipient` (`recipient_email`),
  KEY `idx_emails_sent_at` (`sent_at`),
  KEY `idx_emails_message_id` (`message_id`)
) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `event_banners` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` bigint(20) NOT NULL,
  `file_id` bigint(20) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_file` (`event_id`,`file_id`,`deleted_at`),
  KEY `fk_event_banners_file_id` (`file_id`),
  KEY `idx_event_banners_deleted_at` (`deleted_at`),
  KEY `idx_event_banners_event` (`event_id`),
  KEY `fk_event_banners_on_created_by` (`created_by_id`),
  KEY `fk_event_banners_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_event_banners_event_id` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_event_banners_file_id` FOREIGN KEY (`file_id`) REFERENCES `files` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_event_banners_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_event_banners_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=225 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `event_feedback` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `feedback` varchar(255) NOT NULL,
  `event_id` bigint(20) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_event_feedback_event_id` (`event_id`),
  KEY `idx_event_feedback_deleted_at` (`deleted_at`),
  KEY `fk_event_feedback_on_created_by` (`created_by_id`),
  KEY `fk_event_feedback_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_event_feedback_event_id` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_event_feedback_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_event_feedback_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `event_pictures` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `picture_id` bigint(20) NOT NULL,
  `event_id` bigint(20) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_pictures_event_picture_deleted_at` (`event_id`,`picture_id`,`deleted_at`),
  UNIQUE KEY `uk_event_pictures_picture_deleted_at` (`picture_id`,`deleted_at`),
  KEY `idx_event_pictures_deleted_at` (`deleted_at`),
  KEY `idx_event_pictures_event_id` (`event_id`),
  KEY `idx_event_pictures_picture_id` (`picture_id`),
  KEY `fk_event_pictures_on_created_by` (`created_by_id`),
  KEY `fk_event_pictures_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_event_pictures_event_id` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_event_pictures_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_event_pictures_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_event_pictures_picture_id` FOREIGN KEY (`picture_id`) REFERENCES `files` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `event_sign_up_answers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `answer_id` bigint(20) NOT NULL,
  `event_sign_up_id` bigint(20) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_sign_up_answers_answer_deleted_at` (`answer_id`,`deleted_at`),
  UNIQUE KEY `uk_event_sign_up_answers_signup_answer_deleted_at` (`event_sign_up_id`,`answer_id`,`deleted_at`),
  KEY `idx_event_sign_up_answers_answer_id` (`answer_id`),
  KEY `idx_event_sign_up_answers_deleted_at` (`deleted_at`),
  KEY `idx_event_sign_up_answers_event_sign_up_id` (`event_sign_up_id`),
  CONSTRAINT `fk_event_sign_up_answers_answer_id` FOREIGN KEY (`answer_id`) REFERENCES `answers` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_event_sign_up_answers_event_sign_up_id` FOREIGN KEY (`event_sign_up_id`) REFERENCES `event_signups` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1371 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `event_signups` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` bigint(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `guest_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_signups_event_guest_deleted_at` (`event_id`,`guest_id`,`deleted_at`),
  UNIQUE KEY `uk_event_signups_event_user_deleted_at` (`event_id`,`user_id`,`deleted_at`),
  KEY `fk_event_signups_user_id` (`user_id`),
  KEY `idx_event_signups_deleted_at` (`deleted_at`),
  KEY `idx_event_signups_event_id` (`event_id`),
  KEY `idx_event_signups_guest_id` (`guest_id`),
  KEY `fk_event_signups_on_created_by` (`created_by_id`),
  KEY `fk_event_signups_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_event_signups_event_id` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_event_signups_guest_id` FOREIGN KEY (`guest_id`) REFERENCES `guests` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_event_signups_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_event_signups_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_event_signups_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=682 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `events` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `committee_id` bigint(20) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `description` varchar(4095) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `start_time` datetime NOT NULL,
  `price_member` double DEFAULT NULL,
  `price_public` double DEFAULT NULL,
  `approved` tinyint(1) NOT NULL DEFAULT 0,
  `members_only` bit(1) NOT NULL,
  `sign_up` tinyint(1) NOT NULL,
  `google_id` mediumtext DEFAULT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `end_time` datetime NOT NULL,
  `survey_id` bigint(20) DEFAULT NULL,
  `sign_up_count` bigint(20) unsigned NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  `sign_up_deadline` datetime DEFAULT NULL,
  `sign_up_limit` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_events_google_id_deleted_at` (`google_id`,`deleted_at`) USING HASH,
  KEY `fk_events_committee_id` (`committee_id`),
  KEY `fk_events_survey_id` (`survey_id`),
  KEY `idx_events_approved` (`approved`),
  KEY `idx_events_end_time` (`end_time`),
  KEY `idx_events_members_only` (`members_only`),
  KEY `idx_events_sign_up` (`sign_up`),
  KEY `idx_events_start_time` (`start_time`),
  KEY `idx_events_title` (`title`),
  KEY `idx_events_deleted_at` (`deleted_at`),
  KEY `fk_events_on_created_by` (`created_by_id`),
  KEY `fk_events_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_events_committee_id` FOREIGN KEY (`committee_id`) REFERENCES `committees` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_events_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_events_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_events_survey_id` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=108212 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `external_id_mapping` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `aggregate_type` varchar(64) NOT NULL,
  `aggregate_id` bigint(20) NOT NULL,
  `system` varchar(64) NOT NULL,
  `external_id` varchar(1024) DEFAULT NULL,
  `synced_version` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6) ON UPDATE current_timestamp(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_id_mapping` (`aggregate_type`,`aggregate_id`,`system`),
  KEY `idx_external_id_mapping_system` (`system`,`aggregate_type`),
  KEY `idx_external_id_mapping_lookup` (`aggregate_type`,`system`,`external_id`(191))
) ENGINE=InnoDB AUTO_INCREMENT=2331 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `files` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `uploader_id` bigint(20) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `media_type` varchar(255) NOT NULL,
  `size` bigint(20) DEFAULT NULL,
  `width` int(11) DEFAULT NULL,
  `height` int(11) DEFAULT NULL,
  `source_file_id` bigint(20) DEFAULT NULL,
  `rendition_width` int(11) DEFAULT NULL,
  `type` varchar(255) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `path` longtext NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_files_path_deleted_at` (`path`,`deleted_at`) USING HASH,
  KEY `fk_files_uploader_id` (`uploader_id`),
  KEY `idx_files_created_at` (`created_at`),
  KEY `idx_files_media_type` (`media_type`),
  KEY `idx_files_type` (`type`),
  KEY `idx_files_deleted_at` (`deleted_at`),
  KEY `fk_files_on_created_by` (`created_by_id`),
  KEY `fk_files_on_updated_by` (`updated_by_id`),
  KEY `idx_files_source` (`source_file_id`,`rendition_width`),
  CONSTRAINT `fk_files_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_files_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_files_source` FOREIGN KEY (`source_file_id`) REFERENCES `files` (`id`),
  CONSTRAINT `fk_files_uploader_id` FOREIGN KEY (`uploader_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=1064 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `game` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(32) NOT NULL,
  `name` varchar(64) NOT NULL DEFAULT '',
  `slug` varchar(64) NOT NULL,
  `intro` text DEFAULT NULL,
  `accent` varchar(32) DEFAULT NULL,
  `banner_file_id` bigint(20) DEFAULT NULL,
  `icon_file_id` bigint(20) DEFAULT NULL,
  `sort_index` int(11) NOT NULL DEFAULT 0,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_game_slug` (`slug`,`deleted_at`),
  UNIQUE KEY `uk_game_code` (`code`),
  KEY `idx_game_deleted_at` (`deleted_at`),
  KEY `fk_game_created` (`created_by_id`),
  KEY `fk_game_updated` (`updated_by_id`),
  KEY `fk_game_banner` (`banner_file_id`),
  KEY `fk_game_icon` (`icon_file_id`),
  CONSTRAINT `fk_game_banner` FOREIGN KEY (`banner_file_id`) REFERENCES `files` (`id`),
  CONSTRAINT `fk_game_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_game_icon` FOREIGN KEY (`icon_file_id`) REFERENCES `files` (`id`),
  CONSTRAINT `fk_game_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `guests` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` text NOT NULL,
  `discord` varchar(255) NOT NULL,
  `email` text NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `access_token_hash` varchar(64) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `phone_number` varchar(255) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guests_access_token_hash_deleted_at` (`access_token_hash`,`deleted_at`),
  KEY `idx_guests_created_at` (`created_at`),
  KEY `idx_guests_discord` (`discord`),
  KEY `idx_guests_name` (`name`(768)),
  KEY `idx_guests_deleted_at` (`deleted_at`),
  KEY `fk_guests_on_created_by` (`created_by_id`),
  KEY `fk_guests_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_guests_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_guests_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `incasso_notifications` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `contribution_period_id` bigint(20) NOT NULL,
  `fee_type` varchar(32) NOT NULL,
  `amount` double NOT NULL,
  `debit_date` date NOT NULL,
  `asked_at` datetime NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_incasso_notifications_created_by_id` (`created_by_id`),
  KEY `fk_incasso_notifications_updated_by_id` (`updated_by_id`),
  KEY `idx_incasso_notifications_deleted_at` (`deleted_at`),
  KEY `idx_incasso_notifications_created_at` (`created_at`),
  KEY `idx_incasso_notifications_user_id` (`user_id`,`deleted_at`),
  KEY `idx_incasso_notifications_contribution_period_id` (`contribution_period_id`,`deleted_at`),
  KEY `idx_incasso_notifications_user_period_asked` (`user_id`,`contribution_period_id`,`asked_at`),
  CONSTRAINT `fk_incasso_notifications_contribution_period_id` FOREIGN KEY (`contribution_period_id`) REFERENCES `contribution_periods` (`id`),
  CONSTRAINT `fk_incasso_notifications_created_by_id` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_incasso_notifications_updated_by_id` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_incasso_notifications_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `job_executions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `job_type` varchar(255) NOT NULL,
  `status` varchar(32) NOT NULL,
  `payload` longtext DEFAULT NULL,
  `error_message` longtext DEFAULT NULL,
  `error_type` varchar(255) DEFAULT NULL,
  `error_reason` longtext DEFAULT NULL,
  `attempts` int(11) NOT NULL DEFAULT 0,
  `queued_at` datetime DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `finished_at` datetime DEFAULT NULL,
  `next_attempt_at` datetime DEFAULT NULL,
  `dedup_key` varchar(255) DEFAULT NULL,
  `initiated_by_user_id` bigint(20) DEFAULT NULL,
  `initiated_by_type` varchar(16) NOT NULL DEFAULT 'SYSTEM',
  `initiated_by_role` varchar(32) NOT NULL DEFAULT 'ADMIN',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `fk_job_executions_on_created_by` (`created_by_id`),
  KEY `fk_job_executions_on_updated_by` (`updated_by_id`),
  KEY `idx_job_executions_status` (`status`),
  KEY `idx_job_executions_job_type` (`job_type`),
  KEY `idx_job_executions_created_at` (`created_at`),
  KEY `idx_job_executions_initiated_by_user_id` (`initiated_by_user_id`),
  KEY `idx_job_executions_initiated_by_type` (`initiated_by_type`),
  KEY `idx_job_executions_initiated_by_role` (`initiated_by_role`),
  KEY `idx_job_executions_dedup` (`job_type`,`dedup_key`,`status`),
  KEY `idx_job_executions_due_retry` (`status`,`next_attempt_at`),
  KEY `idx_job_executions_updated_at` (`updated_at`),
  CONSTRAINT `fk_job_executions_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_job_executions_on_initiated_by_user` FOREIGN KEY (`initiated_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_job_executions_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=80038 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `member_profiles` (
  `id` bigint(20) NOT NULL,
  `date_of_birth` date DEFAULT NULL,
  `student_number` varchar(255) DEFAULT NULL,
  `gender` varchar(64) DEFAULT NULL,
  `nationality` varchar(128) DEFAULT NULL,
  `bhv` bit(1) DEFAULT NULL,
  `ehbo` bit(1) DEFAULT NULL,
  `conditions_accepted_at` datetime DEFAULT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `name_on_rosters` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `fk_member_profiles_created_by` (`created_by_id`),
  KEY `fk_member_profiles_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_member_profiles_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_member_profiles_id` FOREIGN KEY (`id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_member_profiles_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `memberships` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date DEFAULT NULL,
  `type` varchar(255) NOT NULL,
  `incasso` bit(1) NOT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_memberships_end_date` (`end_date`),
  KEY `idx_memberships_incasso` (`incasso`),
  KEY `idx_memberships_member_type` (`type`),
  KEY `idx_memberships_start_date` (`start_date`),
  KEY `idx_memberships_deleted_at` (`deleted_at`),
  KEY `idx_memberships_user_id` (`user_id`),
  KEY `fk_memberships_on_created_by` (`created_by_id`),
  KEY `fk_memberships_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_memberships_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_memberships_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_memberships_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=272 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `oauth2_authorization` (
  `id` varchar(100) NOT NULL,
  `registered_client_id` varchar(100) NOT NULL,
  `principal_name` varchar(200) NOT NULL,
  `authorization_grant_type` varchar(100) NOT NULL,
  `authorized_scopes` varchar(1000) DEFAULT NULL,
  `attributes` longtext DEFAULT NULL,
  `state` varchar(500) DEFAULT NULL,
  `authorization_code_value` longtext DEFAULT NULL,
  `authorization_code_issued_at` timestamp NULL DEFAULT NULL,
  `authorization_code_expires_at` timestamp NULL DEFAULT NULL,
  `authorization_code_metadata` longtext DEFAULT NULL,
  `access_token_value` longtext DEFAULT NULL,
  `access_token_issued_at` timestamp NULL DEFAULT NULL,
  `access_token_expires_at` timestamp NULL DEFAULT NULL,
  `access_token_metadata` longtext DEFAULT NULL,
  `access_token_type` varchar(100) DEFAULT NULL,
  `access_token_scopes` varchar(1000) DEFAULT NULL,
  `oidc_id_token_value` longtext DEFAULT NULL,
  `oidc_id_token_issued_at` timestamp NULL DEFAULT NULL,
  `oidc_id_token_expires_at` timestamp NULL DEFAULT NULL,
  `oidc_id_token_metadata` longtext DEFAULT NULL,
  `refresh_token_value` longtext DEFAULT NULL,
  `refresh_token_issued_at` timestamp NULL DEFAULT NULL,
  `refresh_token_expires_at` timestamp NULL DEFAULT NULL,
  `refresh_token_metadata` longtext DEFAULT NULL,
  `user_code_value` longtext DEFAULT NULL,
  `user_code_issued_at` timestamp NULL DEFAULT NULL,
  `user_code_expires_at` timestamp NULL DEFAULT NULL,
  `user_code_metadata` longtext DEFAULT NULL,
  `device_code_value` longtext DEFAULT NULL,
  `device_code_issued_at` timestamp NULL DEFAULT NULL,
  `device_code_expires_at` timestamp NULL DEFAULT NULL,
  `device_code_metadata` longtext DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `oauth2_authorization_consent` (
  `registered_client_id` varchar(100) NOT NULL,
  `principal_name` varchar(200) NOT NULL,
  `authorities` varchar(1000) NOT NULL,
  PRIMARY KEY (`registered_client_id`,`principal_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `oauth2_registered_client` (
  `id` varchar(100) NOT NULL,
  `client_id` varchar(100) NOT NULL,
  `client_id_issued_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `client_secret` varchar(200) DEFAULT NULL,
  `client_secret_expires_at` timestamp NULL DEFAULT NULL,
  `client_name` varchar(200) NOT NULL,
  `client_authentication_methods` varchar(1000) NOT NULL,
  `authorization_grant_types` varchar(1000) NOT NULL,
  `redirect_uris` varchar(1000) DEFAULT NULL,
  `post_logout_redirect_uris` varchar(1000) DEFAULT NULL,
  `scopes` varchar(1000) NOT NULL,
  `client_settings` varchar(2000) NOT NULL,
  `token_settings` varchar(2000) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `questions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `survey_id` bigint(20) NOT NULL,
  `type` varchar(16) NOT NULL,
  `label` varchar(2047) NOT NULL,
  `choice_labels` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`choice_labels`)),
  `idx` int(11) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `answer_count` bigint(20) unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  `required` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_questions_survey_idx_deleted_at` (`survey_id`,`idx`,`deleted_at`),
  KEY `idx_questions_survey_idx` (`survey_id`,`idx`),
  KEY `idx_questions_type` (`type`),
  KEY `idx_questions_deleted_at` (`deleted_at`),
  KEY `idx_questions_survey_id` (`survey_id`),
  KEY `fk_questions_on_created_by` (`created_by_id`),
  KEY `fk_questions_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_questions_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_questions_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_questions_survey_id` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=155 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `recovery_tokens` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `user_id` bigint(20) NOT NULL,
  `type` varchar(50) NOT NULL,
  `selector` varchar(64) NOT NULL,
  `verifier_hash` varchar(255) NOT NULL,
  `expires_at` datetime NOT NULL,
  `consumed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_recovery_selector_deleted_at` (`selector`,`deleted_at`),
  KEY `idx_recovery_tokens_expires` (`expires_at`),
  KEY `idx_recovery_tokens_user_id_type_deleted_at` (`user_id`,`type`,`deleted_at`),
  KEY `FK_RECOVERY_TOKENS_ON_CREATED_BY` (`created_by_id`),
  KEY `FK_RECOVERY_TOKENS_ON_UPDATED_BY` (`updated_by_id`),
  CONSTRAINT `FK_RECOVERY_TOKENS_ON_CREATED_BY` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK_RECOVERY_TOKENS_ON_UPDATED_BY` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK_RECOVERY_TOKENS_ON_USER` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=149 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `redirects` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `telemetry_id` bigint(20) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_redirects_telemetry_id` (`telemetry_id`),
  KEY `idx_redirects_created_at` (`created_at`),
  KEY `idx_redirects_deleted_at` (`deleted_at`),
  KEY `fk_redirects_on_created_by` (`created_by_id`),
  KEY `fk_redirects_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_redirects_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_redirects_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_redirects_telemetry_id` FOREIGN KEY (`telemetry_id`) REFERENCES `telemetries` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `season` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_name` (`name`,`deleted_at`),
  KEY `idx_season_dates` (`start_date`,`end_date`),
  KEY `idx_season_deleted_at` (`deleted_at`),
  KEY `fk_season_created` (`created_by_id`),
  KEY `fk_season_updated` (`updated_by_id`),
  CONSTRAINT `fk_season_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_season_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `season_game` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `season_id` bigint(20) NOT NULL,
  `game` varchar(32) NOT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_game` (`season_id`,`game`,`deleted_at`),
  KEY `idx_season_game_deleted_at` (`deleted_at`),
  KEY `fk_season_game_created` (`created_by_id`),
  KEY `fk_season_game_updated` (`updated_by_id`),
  KEY `fk_season_game_game` (`game`),
  CONSTRAINT `fk_season_game_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_season_game_game` FOREIGN KEY (`game`) REFERENCES `game` (`code`),
  CONSTRAINT `fk_season_game_season` FOREIGN KEY (`season_id`) REFERENCES `season` (`id`),
  CONSTRAINT `fk_season_game_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `sponsors` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `description` varchar(4095) NOT NULL,
  `logo_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sponsors_name_deleted_at` (`name`,`deleted_at`),
  UNIQUE KEY `uk_sponsors_logo_deleted_at` (`logo_id`,`deleted_at`),
  KEY `idx_sponsors_deleted_at` (`deleted_at`),
  KEY `idx_sponsors_logo_id` (`logo_id`),
  KEY `fk_sponsors_on_created_by` (`created_by_id`),
  KEY `fk_sponsors_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_sponsors_logo_id` FOREIGN KEY (`logo_id`) REFERENCES `files` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_sponsors_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_sponsors_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `study_programs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `level` varchar(16) NOT NULL,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_study_programs_name_level_deleted_at` (`name`,`level`,`deleted_at`),
  KEY `fk_study_programs_created_by` (`created_by_id`),
  KEY `fk_study_programs_updated_by` (`updated_by_id`),
  KEY `idx_study_programs_level_active` (`level`,`active`),
  CONSTRAINT `fk_study_programs_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_study_programs_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `surveys` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `response_count` bigint(20) unsigned NOT NULL DEFAULT 0,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_surveys_response_count` (`response_count`),
  KEY `idx_surveys_deleted_at` (`deleted_at`),
  KEY `fk_surveys_on_created_by` (`created_by_id`),
  KEY `fk_surveys_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_surveys_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_surveys_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `team` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `icon_file_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_name` (`name`,`deleted_at`),
  KEY `idx_team_deleted_at` (`deleted_at`),
  KEY `fk_team_created` (`created_by_id`),
  KEY `fk_team_updated` (`updated_by_id`),
  KEY `fk_team_icon` (`icon_file_id`),
  CONSTRAINT `fk_team_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_team_icon` FOREIGN KEY (`icon_file_id`) REFERENCES `files` (`id`),
  CONSTRAINT `fk_team_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `team_roster_entry` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `team_season_id` bigint(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `display_name` varchar(128) DEFAULT NULL,
  `handle` varchar(128) NOT NULL,
  `team_role` varchar(16) NOT NULL,
  `role_title` varchar(64) DEFAULT NULL,
  `description` varchar(280) DEFAULT NULL,
  `icon_file_id` bigint(20) DEFAULT NULL,
  `sort_index` int(11) NOT NULL DEFAULT 0,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_roster_entry` (`team_season_id`,`handle`,`deleted_at`),
  KEY `idx_roster_entry_fielding` (`team_season_id`,`deleted_at`),
  KEY `idx_roster_entry_user` (`user_id`,`deleted_at`),
  KEY `idx_roster_entry_deleted_at` (`deleted_at`),
  KEY `fk_roster_entry_created` (`created_by_id`),
  KEY `fk_roster_entry_updated` (`updated_by_id`),
  KEY `fk_roster_entry_icon` (`icon_file_id`),
  CONSTRAINT `fk_roster_entry_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_roster_entry_fielding` FOREIGN KEY (`team_season_id`) REFERENCES `team_season` (`id`),
  CONSTRAINT `fk_roster_entry_icon` FOREIGN KEY (`icon_file_id`) REFERENCES `files` (`id`),
  CONSTRAINT `fk_roster_entry_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_roster_entry_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=532 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `team_season` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `team_id` bigint(20) NOT NULL,
  `game` varchar(32) NOT NULL,
  `season_id` bigint(20) NOT NULL,
  `banner_file_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_season` (`team_id`,`game`,`season_id`,`deleted_at`),
  KEY `idx_team_season_game` (`game`,`season_id`,`deleted_at`),
  KEY `idx_team_season_season` (`season_id`,`deleted_at`),
  KEY `idx_team_season_deleted_at` (`deleted_at`),
  KEY `fk_team_season_created` (`created_by_id`),
  KEY `fk_team_season_updated` (`updated_by_id`),
  KEY `fk_team_season_banner` (`banner_file_id`),
  CONSTRAINT `fk_team_season_banner` FOREIGN KEY (`banner_file_id`) REFERENCES `files` (`id`),
  CONSTRAINT `fk_team_season_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_team_season_game` FOREIGN KEY (`game`) REFERENCES `game` (`code`),
  CONSTRAINT `fk_team_season_season` FOREIGN KEY (`season_id`) REFERENCES `season` (`id`),
  CONSTRAINT `fk_team_season_team` FOREIGN KEY (`team_id`) REFERENCES `team` (`id`),
  CONSTRAINT `fk_team_season_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=96 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `telemetries` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `url` varchar(255) NOT NULL,
  `platform` smallint(6) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_telemetries_platform_url_deleted_at` (`platform`,`url`,`deleted_at`),
  KEY `idx_telemetries_created_at` (`created_at`),
  KEY `idx_telemetries_platform` (`platform`),
  KEY `idx_telemetries_url` (`url`),
  KEY `idx_telemetries_deleted_at` (`deleted_at`),
  KEY `fk_telemetries_on_created_by` (`created_by_id`),
  KEY `fk_telemetries_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_telemetries_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_telemetries_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `user_game_account` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `game` varchar(32) NOT NULL,
  `handle` varchar(128) NOT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT current_timestamp(6),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL DEFAULT '9999-12-31 23:59:59.000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_game_account` (`user_id`,`game`,`deleted_at`),
  KEY `idx_user_game_account_game` (`game`,`deleted_at`),
  KEY `idx_user_game_account_del` (`deleted_at`),
  KEY `fk_user_game_account_created` (`created_by_id`),
  KEY `fk_user_game_account_updated` (`updated_by_id`),
  CONSTRAINT `fk_user_game_account_created` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_user_game_account_game` FOREIGN KEY (`game`) REFERENCES `game` (`code`),
  CONSTRAINT `fk_user_game_account_updated` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_user_game_account_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `user_studies` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `study_program_id` bigint(20) NOT NULL,
  `status` varchar(16) NOT NULL,
  `start_year` int(11) DEFAULT NULL,
  `graduation_year` int(11) DEFAULT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `updated_by_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `fk_user_studies_program` (`study_program_id`),
  KEY `fk_user_studies_created_by` (`created_by_id`),
  KEY `fk_user_studies_updated_by` (`updated_by_id`),
  KEY `idx_user_studies_user_id` (`user_id`),
  KEY `idx_user_studies_status` (`status`),
  CONSTRAINT `fk_user_studies_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_user_studies_program` FOREIGN KEY (`study_program_id`) REFERENCES `study_programs` (`id`),
  CONSTRAINT `fk_user_studies_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_user_studies_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(255) NOT NULL,
  `password` mediumtext NOT NULL,
  `first_name` mediumtext NOT NULL,
  `last_name` mediumtext NOT NULL,
  `prefix` mediumtext DEFAULT NULL,
  `initials` mediumtext DEFAULT NULL,
  `phone_number` mediumtext DEFAULT NULL,
  `email` mediumtext NOT NULL,
  `created_at` datetime NOT NULL DEFAULT current_timestamp(),
  `discord` varchar(255) DEFAULT NULL,
  `steamid` mediumtext DEFAULT NULL,
  `newsletter` tinyint(1) NOT NULL,
  `consent_privacy` bit(1) DEFAULT NULL,
  `profile_picture_id` bigint(20) DEFAULT NULL,
  `deleted_at` datetime NOT NULL DEFAULT '9999-12-31 23:59:59',
  `enabled` bit(1) NOT NULL,
  `address_id` bigint(20) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT current_timestamp(),
  `version` bigint(20) NOT NULL DEFAULT 0,
  `created_by_id` bigint(20) DEFAULT NULL,
  `updated_by_id` bigint(20) DEFAULT NULL,
  `photo_consent` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username_deleted_at` (`username`,`deleted_at`),
  UNIQUE KEY `uk_users_address_id_deleted_at` (`address_id`,`deleted_at`),
  UNIQUE KEY `uk_users_profile_picture_id_deleted_at` (`profile_picture_id`,`deleted_at`),
  UNIQUE KEY `uk_users_discord_deleted_at` (`discord`,`deleted_at`),
  UNIQUE KEY `uk_users_email_deleted_at` (`email`,`deleted_at`) USING HASH,
  UNIQUE KEY `uk_users_phone_number_deleted_at` (`phone_number`,`deleted_at`) USING HASH,
  KEY `idx_users_created_at` (`created_at`),
  KEY `idx_users_enabled` (`enabled`),
  KEY `idx_users_first_name` (`first_name`(768)),
  KEY `idx_users_last_name` (`last_name`(768)),
  KEY `idx_users_newsletter` (`newsletter`),
  KEY `idx_users_deleted_at` (`deleted_at`),
  KEY `fk_users_on_created_by` (`created_by_id`),
  KEY `fk_users_on_updated_by` (`updated_by_id`),
  CONSTRAINT `fk_users_address_id` FOREIGN KEY (`address_id`) REFERENCES `addresses` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `fk_users_on_created_by` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_users_on_updated_by` FOREIGN KEY (`updated_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_users_profile_picture_id` FOREIGN KEY (`profile_picture_id`) REFERENCES `files` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=297 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

--rollback SET FOREIGN_KEY_CHECKS = 0;
--rollback DROP TABLE IF EXISTS `addresses`;
--rollback DROP TABLE IF EXISTS `answers`;
--rollback DROP TABLE IF EXISTS `authorities`;
--rollback DROP TABLE IF EXISTS `blogs`;
--rollback DROP TABLE IF EXISTS `boards`;
--rollback DROP TABLE IF EXISTS `board_documents`;
--rollback DROP TABLE IF EXISTS `board_members`;
--rollback DROP TABLE IF EXISTS `cohort`;
--rollback DROP TABLE IF EXISTS `cohort_member`;
--rollback DROP TABLE IF EXISTS `cohort_rule`;
--rollback DROP TABLE IF EXISTS `cohort_subject`;
--rollback DROP TABLE IF EXISTS `committees`;
--rollback DROP TABLE IF EXISTS `committee_members`;
--rollback DROP TABLE IF EXISTS `contacts`;
--rollback DROP TABLE IF EXISTS `contact_external_ids`;
--rollback DROP TABLE IF EXISTS `contact_lists`;
--rollback DROP TABLE IF EXISTS `contact_list_external_ids`;
--rollback DROP TABLE IF EXISTS `contact_list_memberships`;
--rollback DROP TABLE IF EXISTS `contributions`;
--rollback DROP TABLE IF EXISTS `contribution_periods`;
--rollback DROP TABLE IF EXISTS `contribution_reminders`;
--rollback DROP TABLE IF EXISTS `deleted_users`;
--rollback DROP TABLE IF EXISTS `emails`;
--rollback DROP TABLE IF EXISTS `events`;
--rollback DROP TABLE IF EXISTS `event_banners`;
--rollback DROP TABLE IF EXISTS `event_feedback`;
--rollback DROP TABLE IF EXISTS `event_pictures`;
--rollback DROP TABLE IF EXISTS `EVENT_PUBLICATION`;
--rollback DROP TABLE IF EXISTS `event_signups`;
--rollback DROP TABLE IF EXISTS `event_sign_up_answers`;
--rollback DROP TABLE IF EXISTS `external_id_mapping`;
--rollback DROP TABLE IF EXISTS `files`;
--rollback DROP TABLE IF EXISTS `game`;
--rollback DROP TABLE IF EXISTS `guests`;
--rollback DROP TABLE IF EXISTS `incasso_notifications`;
--rollback DROP TABLE IF EXISTS `job_executions`;
--rollback DROP TABLE IF EXISTS `memberships`;
--rollback DROP TABLE IF EXISTS `member_profiles`;
--rollback DROP TABLE IF EXISTS `oauth2_authorization`;
--rollback DROP TABLE IF EXISTS `oauth2_authorization_consent`;
--rollback DROP TABLE IF EXISTS `oauth2_registered_client`;
--rollback DROP TABLE IF EXISTS `questions`;
--rollback DROP TABLE IF EXISTS `recovery_tokens`;
--rollback DROP TABLE IF EXISTS `redirects`;
--rollback DROP TABLE IF EXISTS `season`;
--rollback DROP TABLE IF EXISTS `season_game`;
--rollback DROP TABLE IF EXISTS `sponsors`;
--rollback DROP TABLE IF EXISTS `study_programs`;
--rollback DROP TABLE IF EXISTS `surveys`;
--rollback DROP TABLE IF EXISTS `team`;
--rollback DROP TABLE IF EXISTS `team_roster_entry`;
--rollback DROP TABLE IF EXISTS `team_season`;
--rollback DROP TABLE IF EXISTS `telemetries`;
--rollback DROP TABLE IF EXISTS `users`;
--rollback DROP TABLE IF EXISTS `user_game_account`;
--rollback DROP TABLE IF EXISTS `user_studies`;
--rollback SET FOREIGN_KEY_CHECKS = 1;

--changeset baseline:system-account splitStatements:true
--comment The account that owns the files the site ships with. It cannot sign in: `enabled` is false and the password is not a hash.
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM users WHERE username = 'system'

INSERT INTO users (username, password, first_name, last_name, initials, email,
                   newsletter, enabled, photo_consent, consent_privacy)
VALUES ('system', '!', 'Blue', 'Shell', 'BS', 'system@esa-blueshell.nl',
        0, b'0', 0, b'0');

INSERT INTO authorities (user_id, authority)
VALUES (LAST_INSERT_ID(), 'SYSTEM');

--rollback DELETE FROM authorities WHERE authority = 'SYSTEM' AND user_id = (SELECT id FROM users WHERE username = 'system');
--rollback DELETE FROM users WHERE username = 'system';

--changeset baseline:trigger-trg_answers_ad splitStatements:false
CREATE TRIGGER trg_answers_ad AFTER DELETE ON answers FOR EACH ROW BEGIN
    IF OLD.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE questions
        SET answer_count = GREATEST(answer_count - 1, 0)
        WHERE id = OLD.question_id;
    END IF;
END
--rollback DROP TRIGGER IF EXISTS trg_answers_ad;

--changeset baseline:trigger-trg_answers_ai splitStatements:false
CREATE TRIGGER trg_answers_ai AFTER INSERT ON answers FOR EACH ROW BEGIN
    IF NEW.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE questions
        SET answer_count = answer_count + 1
        WHERE id = NEW.question_id;
    END IF;
END
--rollback DROP TRIGGER IF EXISTS trg_answers_ai;

--changeset baseline:trigger-trg_answers_au splitStatements:false
CREATE TRIGGER trg_answers_au AFTER UPDATE ON answers FOR EACH ROW BEGIN
    IF OLD.deleted_at = '9999-12-31 23:59:59' AND NEW.deleted_at <> '9999-12-31 23:59:59' THEN
        UPDATE questions
        SET answer_count = GREATEST(answer_count - 1, 0)
        WHERE id = OLD.question_id;
    END IF;

    IF OLD.deleted_at <> '9999-12-31 23:59:59' AND NEW.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE questions
        SET answer_count = answer_count + 1
        WHERE id = NEW.question_id;
    END IF;

    IF OLD.deleted_at = '9999-12-31 23:59:59'
        AND NEW.deleted_at = '9999-12-31 23:59:59'
        AND OLD.question_id <> NEW.question_id THEN
        UPDATE questions SET answer_count = GREATEST(answer_count - 1, 0) WHERE id = OLD.question_id;
        UPDATE questions SET answer_count = answer_count + 1 WHERE id = NEW.question_id;
    END IF;
END
--rollback DROP TRIGGER IF EXISTS trg_answers_au;

--changeset baseline:trigger-trg_event_signups_ad splitStatements:false
CREATE TRIGGER trg_event_signups_ad AFTER DELETE ON event_signups FOR EACH ROW BEGIN
    IF OLD.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE events
        SET sign_up_count = GREATEST(sign_up_count - 1, 0)
        WHERE id = OLD.event_id;
    END IF;
END
--rollback DROP TRIGGER IF EXISTS trg_event_signups_ad;

--changeset baseline:trigger-trg_event_signups_ai splitStatements:false
CREATE TRIGGER trg_event_signups_ai AFTER INSERT ON event_signups FOR EACH ROW BEGIN
    IF NEW.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE events
        SET sign_up_count = sign_up_count + 1
        WHERE id = NEW.event_id;
    END IF;
END
--rollback DROP TRIGGER IF EXISTS trg_event_signups_ai;

--changeset baseline:trigger-trg_event_signups_au splitStatements:false
CREATE TRIGGER trg_event_signups_au AFTER UPDATE ON event_signups FOR EACH ROW BEGIN
    IF OLD.deleted_at = '9999-12-31 23:59:59' AND NEW.deleted_at <> '9999-12-31 23:59:59' THEN
        UPDATE events
        SET sign_up_count = GREATEST(sign_up_count - 1, 0)
        WHERE id = OLD.event_id;
    END IF;

    IF OLD.deleted_at <> '9999-12-31 23:59:59' AND NEW.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE events
        SET sign_up_count = sign_up_count + 1
        WHERE id = NEW.event_id;
    END IF;

    IF OLD.deleted_at = '9999-12-31 23:59:59'
        AND NEW.deleted_at = '9999-12-31 23:59:59'
        AND OLD.event_id <> NEW.event_id THEN
        UPDATE events SET sign_up_count = GREATEST(sign_up_count - 1, 0) WHERE id = OLD.event_id;
        UPDATE events SET sign_up_count = sign_up_count + 1 WHERE id = NEW.event_id;
    END IF;
END
--rollback DROP TRIGGER IF EXISTS trg_event_signups_au;

--changeset baseline:trigger-trg_questions_ad splitStatements:false
CREATE TRIGGER trg_questions_ad AFTER DELETE ON questions FOR EACH ROW BEGIN
    IF OLD.deleted_at = '9999-12-31 23:59:59' AND OLD.type <> 'DESCRIPTION' THEN
        UPDATE surveys s
        SET s.response_count = COALESCE((SELECT MAX(q.answer_count)
                                         FROM questions q
                                         WHERE q.survey_id = OLD.survey_id
                                           AND q.deleted_at = '9999-12-31 23:59:59'
                                           AND q.type <> 'DESCRIPTION'), 0)
        WHERE s.id = OLD.survey_id;
    END IF;
END
--rollback DROP TRIGGER IF EXISTS trg_questions_ad;

--changeset baseline:trigger-trg_questions_ai splitStatements:false
CREATE TRIGGER trg_questions_ai AFTER INSERT ON questions FOR EACH ROW BEGIN
    IF NEW.deleted_at = '9999-12-31 23:59:59' AND NEW.type <> 'DESCRIPTION' THEN
        UPDATE surveys s
        SET s.response_count = GREATEST(s.response_count, NEW.answer_count)
        WHERE s.id = NEW.survey_id;
    END IF;
END
--rollback DROP TRIGGER IF EXISTS trg_questions_ai;

--changeset baseline:trigger-trg_questions_au splitStatements:false
CREATE TRIGGER trg_questions_au AFTER UPDATE ON questions FOR EACH ROW BEGIN
    DECLARE old_sid BIGINT;
    DECLARE new_sid BIGINT;
    SET old_sid = OLD.survey_id;
    SET new_sid = NEW.survey_id;

    IF NEW.deleted_at = '9999-12-31 23:59:59' AND NEW.type <> 'DESCRIPTION' THEN
        UPDATE surveys s
        SET s.response_count = GREATEST(s.response_count, NEW.answer_count)
        WHERE s.id = new_sid;
    END IF;

    IF (OLD.deleted_at = '9999-12-31 23:59:59' AND OLD.type <> 'DESCRIPTION')
        AND (new_sid <> old_sid
            OR NEW.deleted_at <> '9999-12-31 23:59:59'
            OR NEW.type = 'DESCRIPTION'
            OR NEW.answer_count < OLD.answer_count) THEN
        UPDATE surveys s
        SET s.response_count = COALESCE((SELECT MAX(q.answer_count)
                                         FROM questions q
                                         WHERE q.survey_id = old_sid
                                           AND q.deleted_at = '9999-12-31 23:59:59'
                                           AND q.type <> 'DESCRIPTION'), 0)
        WHERE s.id = old_sid;
    END IF;
END
--rollback DROP TRIGGER IF EXISTS trg_questions_au;

