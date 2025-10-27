CREATE INDEX idx_addresses_deleted_at ON addresses (deleted_at);

CREATE INDEX idx_answers_deleted_at ON answers (deleted_at);

CREATE INDEX idx_blogs_deleted_at ON blogs (deleted_at);

CREATE INDEX idx_board_documents_board_id ON board_documents (board_id);

CREATE INDEX idx_board_documents_deleted_at ON board_documents (deleted_at);

CREATE INDEX idx_board_documents_file_id ON board_documents (file_id);

CREATE INDEX idx_board_members_board_id ON board_members (board_id);

CREATE INDEX idx_board_members_deleted_at ON board_members (deleted_at);

CREATE INDEX idx_boards_deleted_at ON boards (deleted_at);

CREATE INDEX idx_committee_members_committee_id ON committee_members (committee_id);

CREATE INDEX idx_committee_members_deleted_at ON committee_members (deleted_at);

CREATE INDEX idx_committees_deleted_at ON committees (deleted_at);

CREATE INDEX idx_contribution_periods_deleted_at ON contribution_periods (deleted_at);

CREATE INDEX idx_contributions_deleted_at ON contributions (deleted_at);

CREATE INDEX idx_contributions_user_id ON contributions (user_id);

CREATE INDEX idx_event_banners_deleted_at ON event_banners (deleted_at);

CREATE INDEX idx_event_banners_event ON event_banners (event_id);

CREATE INDEX idx_event_feedback_deleted_at ON event_feedback (deleted_at);

CREATE INDEX idx_event_pictures_deleted_at ON event_pictures (deleted_at);

CREATE INDEX idx_event_pictures_event_id ON event_pictures (event_id);

CREATE INDEX idx_event_pictures_picture_id ON event_pictures (picture_id);

CREATE INDEX idx_event_sign_up_answers_answer_id ON event_sign_up_answers (answer_id);

CREATE INDEX idx_event_sign_up_answers_deleted_at ON event_sign_up_answers (deleted_at);

CREATE INDEX idx_event_sign_up_answers_event_sign_up_id ON event_sign_up_answers (event_sign_up_id);

CREATE INDEX idx_event_signups_deleted_at ON event_signups (deleted_at);

CREATE INDEX idx_event_signups_event_id ON event_signups (event_id);

CREATE INDEX idx_event_signups_guest_id ON event_signups (guest_id);

CREATE INDEX idx_events_deleted_at ON events (deleted_at);

CREATE INDEX idx_files_deleted_at ON files (deleted_at);

CREATE INDEX idx_guests_deleted_at ON guests (deleted_at);

CREATE INDEX idx_memberships_deleted_at ON memberships (deleted_at);

CREATE INDEX idx_memberships_user_id ON memberships (user_id);

CREATE INDEX idx_questions_deleted_at ON questions (deleted_at);

CREATE INDEX idx_questions_survey_id ON questions (survey_id);

CREATE INDEX idx_redirects_deleted_at ON redirects (deleted_at);

CREATE INDEX idx_sponsors_deleted_at ON sponsors (deleted_at);

CREATE INDEX idx_sponsors_logo_id ON sponsors (logo_id);

CREATE INDEX idx_surveys_deleted_at ON surveys (deleted_at);

CREATE INDEX idx_telemetries_deleted_at ON telemetries (deleted_at);

CREATE INDEX idx_users_deleted_at ON users (deleted_at);

ALTER TABLE board_documents
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE board_members
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE boards
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE committee_members
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE committees
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE contribution_periods
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE contributions
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE event_banners
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE event_feedback
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE event_pictures
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE event_signups
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE events
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE guests
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE memberships
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);

ALTER TABLE sponsors
    ALTER created_at SET DEFAULT (CURRENT_TIMESTAMP);