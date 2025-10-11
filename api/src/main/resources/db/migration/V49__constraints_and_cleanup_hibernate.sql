ALTER TABLE events
    DROP FOREIGN KEY fk_events_banner_id;

ALTER TABLE news
    DROP FOREIGN KEY fk_news_creator_id;

ALTER TABLE news
    DROP FOREIGN KEY fk_news_last_editor_id;

ALTER TABLE board_documents
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE board_documents
    MODIFY created_at datetime NOT NULL;

ALTER TABLE board_members
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE board_members
    MODIFY created_at datetime NOT NULL;

ALTER TABLE boards
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE boards
    MODIFY created_at datetime NOT NULL;

ALTER TABLE committee_members
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE committee_members
    MODIFY created_at datetime NOT NULL;

ALTER TABLE committees
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE committees
    MODIFY created_at datetime NOT NULL;

ALTER TABLE contribution_periods
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE contribution_periods
    MODIFY created_at datetime NOT NULL;

ALTER TABLE contributions
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE contributions
    MODIFY created_at datetime NOT NULL;

ALTER TABLE event_banners
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE event_banners
    MODIFY created_at datetime NOT NULL;

ALTER TABLE event_feedback
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE event_feedback
    MODIFY created_at datetime NOT NULL;

ALTER TABLE event_pictures
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE event_pictures
    MODIFY created_at datetime NOT NULL;

ALTER TABLE event_signups
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE event_signups
    MODIFY created_at datetime NOT NULL;

ALTER TABLE events
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE events
    MODIFY created_at datetime NOT NULL;

ALTER TABLE memberships
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE memberships
    MODIFY created_at datetime NOT NULL;

ALTER TABLE sponsors
    ADD created_at datetime DEFAULT NOW() NULL;

ALTER TABLE sponsors
    MODIFY created_at datetime NOT NULL;

ALTER TABLE blogs
    ADD CONSTRAINT uk_blogs_title_deleted_at UNIQUE (title, deleted_at);

ALTER TABLE board_documents
    ADD CONSTRAINT uk_board_documents_board_name_deleted_at UNIQUE (board_id, name, deleted_at);

ALTER TABLE board_documents
    ADD CONSTRAINT uk_board_documents_file_deleted_at UNIQUE (file_id, deleted_at);

ALTER TABLE board_members
    ADD CONSTRAINT uk_board_members_board_user_deleted_at UNIQUE (board_id, user_id, deleted_at);

ALTER TABLE board_members
    ADD CONSTRAINT uk_board_members_picture_deleted_at UNIQUE (picture_id, deleted_at);

ALTER TABLE boards
    ADD CONSTRAINT uk_boards_name_start_date_deleted_at UNIQUE (name, start_date, deleted_at);

ALTER TABLE boards
    ADD CONSTRAINT uk_boards_picture_deleted_at UNIQUE (picture_id, deleted_at);

ALTER TABLE committee_members
    ADD CONSTRAINT uk_committee_members_committee_user_deleted_at UNIQUE (committee_id, user_id, deleted_at);

ALTER TABLE committees
    ADD CONSTRAINT uk_committees_name_deleted_at UNIQUE (name, deleted_at);

ALTER TABLE contribution_periods
    ADD CONSTRAINT uk_contribution_periods_start_end_deleted_at UNIQUE (start_date, end_date, deleted_at);

ALTER TABLE contributions
    ADD CONSTRAINT uk_contributions_user_period_deleted_at UNIQUE (user_id, contribution_period_id, deleted_at);

ALTER TABLE event_banners
    ADD CONSTRAINT uk_event_file UNIQUE (event_id, file_id, deleted_at);

ALTER TABLE event_pictures
    ADD CONSTRAINT uk_event_pictures_event_picture_deleted_at UNIQUE (event_id, picture_id, deleted_at);

ALTER TABLE event_pictures
    ADD CONSTRAINT uk_event_pictures_picture_deleted_at UNIQUE (picture_id, deleted_at);

ALTER TABLE event_sign_up_answers
    ADD CONSTRAINT uk_event_sign_up_answers_answer_deleted_at UNIQUE (answer_id, deleted_at);

ALTER TABLE event_sign_up_answers
    ADD CONSTRAINT uk_event_sign_up_answers_signup_answer_deleted_at UNIQUE (event_sign_up_id, answer_id, deleted_at);

ALTER TABLE event_signups
    ADD CONSTRAINT uk_event_signups_event_guest_deleted_at UNIQUE (event_id, guest_id, deleted_at);

ALTER TABLE event_signups
    ADD CONSTRAINT uk_event_signups_event_user_deleted_at UNIQUE (event_id, user_id, deleted_at);

ALTER TABLE event_signups
    ADD CONSTRAINT uk_event_signups_guest_deleted_at UNIQUE (guest_id, deleted_at);

ALTER TABLE events
    ADD CONSTRAINT uk_events_google_id_deleted_at UNIQUE (google_id, deleted_at);

ALTER TABLE files
    ADD CONSTRAINT uk_files_path_deleted_at UNIQUE (`path`, deleted_at);

ALTER TABLE guests
    ADD CONSTRAINT uk_guests_access_token UNIQUE (access_token);

ALTER TABLE guests
    ADD CONSTRAINT uk_guests_email_deleted_at UNIQUE (email, deleted_at);

ALTER TABLE memberships
    ADD CONSTRAINT uk_memberships_signature_deleted_at UNIQUE (signature_id, deleted_at);

ALTER TABLE memberships
    ADD CONSTRAINT uk_memberships_user_id_deleted_at UNIQUE (user_id, deleted_at);

ALTER TABLE questions
    ADD CONSTRAINT uk_questions_survey_idx_deleted_at UNIQUE (survey_id, idx, deleted_at);

ALTER TABLE sponsors
    ADD CONSTRAINT uk_sponsors_logo_deleted_at UNIQUE (logo_id, deleted_at);

ALTER TABLE sponsors
    ADD CONSTRAINT uk_sponsors_name_deleted_at UNIQUE (name, deleted_at);

ALTER TABLE telemetries
    ADD CONSTRAINT uk_telemetries_platform_url_deleted_at UNIQUE (platform, url, deleted_at);

ALTER TABLE users
    ADD CONSTRAINT uk_users_address_id_deleted_at UNIQUE (address_id, deleted_at);

ALTER TABLE users
    ADD CONSTRAINT uk_users_discord_deleted_at UNIQUE (discord, deleted_at);

ALTER TABLE users
    ADD CONSTRAINT uk_users_email_deleted_at UNIQUE (email, deleted_at);

ALTER TABLE users
    ADD CONSTRAINT uk_users_phone_number_deleted_at UNIQUE (phone_number, deleted_at);

ALTER TABLE users
    ADD CONSTRAINT uk_users_profile_picture_id_deleted_at UNIQUE (profile_picture_id, deleted_at);

ALTER TABLE users
    ADD CONSTRAINT uk_users_reset_key_deleted_at UNIQUE (reset_key, deleted_at);

ALTER TABLE users
    ADD CONSTRAINT uk_users_student_number_deleted_at UNIQUE (student_number, deleted_at);

ALTER TABLE users
    ADD CONSTRAINT uk_users_username_deleted_at UNIQUE (username, deleted_at);

CREATE INDEX idx_addresses_city ON addresses (city);

CREATE INDEX idx_addresses_zip_code ON addresses (zip_code);

CREATE INDEX idx_blogs_created_at ON blogs (created_at);

CREATE INDEX idx_blogs_published_at ON blogs (published_at);

CREATE INDEX idx_blogs_title ON blogs (title);

CREATE INDEX idx_boards_end_date ON boards (end_date);

CREATE INDEX idx_boards_name ON boards (name);

CREATE INDEX idx_boards_start_date ON boards (start_date);

CREATE INDEX idx_committee_members_committee_role ON committee_members (committee_id, `role`);

CREATE INDEX idx_committees_name ON committees (name);

CREATE INDEX idx_contribution_periods_end_date ON contribution_periods (end_date);

CREATE INDEX idx_contribution_periods_list_id ON contribution_periods (list_id);

CREATE INDEX idx_contribution_periods_start_date ON contribution_periods (start_date);

CREATE INDEX idx_contributions_paid ON contributions (paid);

CREATE INDEX idx_contributions_reminded_at ON contributions (reminded_at);

CREATE INDEX idx_event_signups_signed_up_at ON event_signups (signed_up_at);

CREATE INDEX idx_events_approved ON events (approved);

CREATE INDEX idx_events_end_time ON events (end_time);

CREATE INDEX idx_events_members_only ON events (members_only);

CREATE INDEX idx_events_sign_up ON events (sign_up);

CREATE INDEX idx_events_start_time ON events (start_time);

CREATE INDEX idx_events_title ON events (title);

CREATE INDEX idx_files_created_at ON files (created_at);

CREATE INDEX idx_files_media_type ON files (media_type);

CREATE INDEX idx_files_type ON files (type);

CREATE INDEX idx_guests_created_at ON guests (created_at);

CREATE INDEX idx_guests_discord ON guests (discord);

CREATE INDEX idx_guests_name ON guests (name);

CREATE INDEX idx_memberships_city ON memberships (city);

CREATE INDEX idx_memberships_country ON memberships (country);

CREATE INDEX idx_memberships_end_date ON memberships (end_date);

CREATE INDEX idx_memberships_incasso ON memberships (incasso);

CREATE INDEX idx_memberships_member_type ON memberships (type);

CREATE INDEX idx_memberships_start_date ON memberships (start_date);

CREATE INDEX idx_questions_survey_idx ON questions (survey_id, idx);

CREATE INDEX idx_questions_type ON questions (type);

CREATE INDEX idx_redirects_created_at ON redirects (created_at);

CREATE INDEX idx_surveys_response_count ON surveys (response_count);

CREATE INDEX idx_telemetries_created_at ON telemetries (created_at);

CREATE INDEX idx_telemetries_platform ON telemetries (platform);

CREATE INDEX idx_telemetries_url ON telemetries (url);

CREATE INDEX idx_users_created_at ON users (created_at);

CREATE INDEX idx_users_enabled ON users (enabled);

CREATE INDEX idx_users_first_name ON users (first_name);

CREATE INDEX idx_users_last_name ON users (last_name);

CREATE INDEX idx_users_newsletter ON users (newsletter);

CREATE INDEX idx_users_reset_key ON users (reset_key);

CREATE INDEX idx_users_reset_key_valid_until ON users (reset_key_valid_until);

DROP TABLE news;

ALTER TABLE authorities
    DROP PRIMARY KEY;

ALTER TABLE events
    DROP COLUMN banner_id;

ALTER TABLE events
    DROP COLUMN max_participants;

ALTER TABLE blogs
    DROP COLUMN markdown;

ALTER TABLE blogs
    DROP COLUMN text;

ALTER TABLE guests
    MODIFY access_token VARCHAR(255) NOT NULL;

ALTER TABLE authorities
    MODIFY authority VARCHAR(255) NULL;

ALTER TABLE board_documents
    MODIFY board_id BIGINT NOT NULL;

ALTER TABLE board_members
    MODIFY board_id BIGINT NOT NULL;

ALTER TABLE boards
    MODIFY candidate VARCHAR(255) NOT NULL;

ALTER TABLE users
    MODIFY consent_gdpr BIT(1) NULL;

ALTER TABLE users
    MODIFY consent_privacy BIT(1) NULL;

ALTER TABLE memberships
    MODIFY country VARCHAR(255) NULL;

ALTER TABLE addresses
    MODIFY created_at datetime NOT NULL;

ALTER TABLE blogs
    MODIFY created_at datetime NOT NULL;

ALTER TABLE blogs
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE files
    MODIFY created_at datetime NOT NULL;

ALTER TABLE files
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE guests
    MODIFY created_at datetime NOT NULL;

ALTER TABLE redirects
    MODIFY created_at datetime NOT NULL;

ALTER TABLE redirects
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE telemetries
    MODIFY created_at datetime NOT NULL;

ALTER TABLE telemetries
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE users
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE committees
    MODIFY `description` VARCHAR(255) NOT NULL;

ALTER TABLE events
    MODIFY `description` VARCHAR(255) NOT NULL;

ALTER TABLE sponsors
    MODIFY `description` VARCHAR(255) NOT NULL;

ALTER TABLE guests
    MODIFY discord VARCHAR(255) NOT NULL;

ALTER TABLE users
    MODIFY discord VARCHAR(255) NOT NULL;

ALTER TABLE users
    MODIFY enabled BIT(1) NOT NULL;

ALTER TABLE events
    MODIFY end_time datetime NOT NULL;

ALTER TABLE event_feedback
    MODIFY event_id BIGINT NOT NULL;

ALTER TABLE event_pictures
    MODIFY event_id BIGINT NOT NULL;

ALTER TABLE event_feedback
    MODIFY feedback VARCHAR(255) NOT NULL;

ALTER TABLE board_documents
    MODIFY file_id BIGINT NOT NULL;

ALTER TABLE blogs
    MODIFY html VARCHAR(255) NOT NULL;

ALTER TABLE memberships
    MODIFY incasso BIT(1) NOT NULL;

ALTER TABLE questions
    MODIFY label VARCHAR(255) NOT NULL;

ALTER TABLE events
    MODIFY location VARCHAR(255) NOT NULL;

ALTER TABLE sponsors
    MODIFY logo_id BIGINT NOT NULL;

ALTER TABLE files
    MODIFY media_type VARCHAR(255) NOT NULL;

ALTER TABLE contributions
    MODIFY member_id BIGINT NOT NULL;

ALTER TABLE events
    MODIFY members_only BIT(1) NOT NULL;

ALTER TABLE board_documents
    MODIFY name VARCHAR(255) NOT NULL;

ALTER TABLE boards
    MODIFY name VARCHAR(255) NOT NULL;

ALTER TABLE committees
    MODIFY name VARCHAR(255) NOT NULL;

ALTER TABLE files
    MODIFY name VARCHAR(255) NOT NULL;

ALTER TABLE sponsors
    MODIFY name VARCHAR(255) NOT NULL;

ALTER TABLE contributions
    MODIFY paid BIT(1) NOT NULL;

ALTER TABLE event_pictures
    MODIFY picture_id BIGINT NOT NULL;

ALTER TABLE telemetries
    MODIFY platform SMALLINT NOT NULL;

ALTER TABLE blogs
    MODIFY published_at datetime NOT NULL;

ALTER TABLE event_signups
    MODIFY signed_up_at datetime NOT NULL;

ALTER TABLE boards
    MODIFY start_date datetime NOT NULL;

ALTER TABLE contribution_periods
    MODIFY start_date date NOT NULL;

ALTER TABLE memberships
    MODIFY start_date date NOT NULL;

ALTER TABLE events
    MODIFY start_time datetime NOT NULL;

ALTER TABLE users
    MODIFY student_number VARCHAR(255) NOT NULL;

ALTER TABLE redirects
    MODIFY telemetry_id BIGINT NOT NULL;

ALTER TABLE blogs
    MODIFY title VARCHAR(255) NOT NULL;

ALTER TABLE files
    MODIFY type VARCHAR(255) NOT NULL;

ALTER TABLE memberships
    MODIFY type VARCHAR(255) NOT NULL;

ALTER TABLE files
    MODIFY uploader_id BIGINT NOT NULL;

ALTER TABLE telemetries
    MODIFY url VARCHAR(255) NOT NULL;

ALTER TABLE board_members
    MODIFY user_id BIGINT NOT NULL;

ALTER TABLE memberships
    MODIFY user_id BIGINT NOT NULL;

ALTER TABLE users
    MODIFY username VARCHAR(255) NOT NULL;