ALTER TABLE addresses
    ADD created_by_id BIGINT NULL;

ALTER TABLE addresses
    ADD updated_by_id BIGINT NULL;

ALTER TABLE answers
    ADD created_by_id BIGINT NULL;

ALTER TABLE answers
    ADD updated_by_id BIGINT NULL;

ALTER TABLE blogs
    ADD created_by_id BIGINT NULL;

ALTER TABLE blogs
    ADD updated_by_id BIGINT NULL;

ALTER TABLE board_documents
    ADD created_by_id BIGINT NULL;

ALTER TABLE board_documents
    ADD updated_by_id BIGINT NULL;

ALTER TABLE board_members
    ADD created_by_id BIGINT NULL;

ALTER TABLE board_members
    ADD updated_by_id BIGINT NULL;

ALTER TABLE boards
    ADD created_by_id BIGINT NULL;

ALTER TABLE boards
    ADD updated_by_id BIGINT NULL;

ALTER TABLE committee_members
    ADD created_by_id BIGINT NULL;

ALTER TABLE committee_members
    ADD updated_by_id BIGINT NULL;

ALTER TABLE committees
    ADD created_by_id BIGINT NULL;

ALTER TABLE committees
    ADD updated_by_id BIGINT NULL;

ALTER TABLE contribution_periods
    ADD created_by_id BIGINT NULL;

ALTER TABLE contribution_periods
    ADD updated_by_id BIGINT NULL;

ALTER TABLE contribution_reminders
    ADD created_by_id BIGINT NULL;

ALTER TABLE contribution_reminders
    ADD updated_by_id BIGINT NULL;

ALTER TABLE contributions
    ADD created_by_id BIGINT NULL;

ALTER TABLE contributions
    ADD updated_by_id BIGINT NULL;

ALTER TABLE event_banners
    ADD created_by_id BIGINT NULL;

ALTER TABLE event_banners
    ADD updated_by_id BIGINT NULL;

ALTER TABLE event_feedback
    ADD created_by_id BIGINT NULL;

ALTER TABLE event_feedback
    ADD updated_by_id BIGINT NULL;

ALTER TABLE event_pictures
    ADD created_by_id BIGINT NULL;

ALTER TABLE event_pictures
    ADD updated_by_id BIGINT NULL;

ALTER TABLE event_sign_up_answers
    ADD created_by_id BIGINT NULL;

ALTER TABLE event_sign_up_answers
    ADD updated_by_id BIGINT NULL;

ALTER TABLE event_signups
    ADD created_by_id BIGINT NULL;

ALTER TABLE event_signups
    ADD updated_by_id BIGINT NULL;

ALTER TABLE events
    ADD created_by_id BIGINT NULL;

ALTER TABLE events
    ADD updated_by_id BIGINT NULL;

ALTER TABLE files
    ADD created_by_id BIGINT NULL;

ALTER TABLE files
    ADD updated_by_id BIGINT NULL;

ALTER TABLE guests
    ADD created_by_id BIGINT NULL;

ALTER TABLE guests
    ADD updated_by_id BIGINT NULL;

ALTER TABLE memberships
    ADD created_by_id BIGINT NULL;

ALTER TABLE memberships
    ADD updated_by_id BIGINT NULL;

ALTER TABLE questions
    ADD created_by_id BIGINT NULL;

ALTER TABLE questions
    ADD updated_by_id BIGINT NULL;

ALTER TABLE redirects
    ADD created_by_id BIGINT NULL;

ALTER TABLE redirects
    ADD updated_by_id BIGINT NULL;

ALTER TABLE sponsors
    ADD created_by_id BIGINT NULL;

ALTER TABLE sponsors
    ADD updated_by_id BIGINT NULL;

ALTER TABLE surveys
    ADD created_by_id BIGINT NULL;

ALTER TABLE surveys
    ADD updated_by_id BIGINT NULL;

ALTER TABLE telemetries
    ADD created_by_id BIGINT NULL;

ALTER TABLE telemetries
    ADD updated_by_id BIGINT NULL;

ALTER TABLE users
    ADD created_by_id BIGINT NULL;

ALTER TABLE users
    ADD updated_by_id BIGINT NULL;

ALTER TABLE addresses
    ADD CONSTRAINT fk_addresses_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE addresses
    ADD CONSTRAINT fk_addresses_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE answers
    ADD CONSTRAINT fk_answers_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE answers
    ADD CONSTRAINT fk_answers_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE blogs
    ADD CONSTRAINT fk_blogs_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE blogs
    ADD CONSTRAINT fk_blogs_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE boards
    ADD CONSTRAINT fk_boards_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE boards
    ADD CONSTRAINT fk_boards_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE board_documents
    ADD CONSTRAINT fk_board_documents_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE board_documents
    ADD CONSTRAINT fk_board_documents_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE board_members
    ADD CONSTRAINT fk_board_members_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE board_members
    ADD CONSTRAINT fk_board_members_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE committees
    ADD CONSTRAINT fk_committees_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE committees
    ADD CONSTRAINT fk_committees_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE committee_members
    ADD CONSTRAINT fk_committee_members_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE committee_members
    ADD CONSTRAINT fk_committee_members_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE contributions
    ADD CONSTRAINT fk_contributions_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE contributions
    ADD CONSTRAINT fk_contributions_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE contribution_periods
    ADD CONSTRAINT fk_contribution_periods_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE contribution_periods
    ADD CONSTRAINT fk_contribution_periods_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE contribution_reminders
    ADD CONSTRAINT fk_contribution_reminders_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE contribution_reminders
    ADD CONSTRAINT fk_contribution_reminders_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE events
    ADD CONSTRAINT fk_events_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE events
    ADD CONSTRAINT fk_events_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE event_banners
    ADD CONSTRAINT fk_event_banners_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE event_banners
    ADD CONSTRAINT fk_event_banners_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE event_feedback
    ADD CONSTRAINT fk_event_feedback_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE event_feedback
    ADD CONSTRAINT fk_event_feedback_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE event_pictures
    ADD CONSTRAINT fk_event_pictures_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE event_pictures
    ADD CONSTRAINT fk_event_pictures_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE event_signups
    ADD CONSTRAINT fk_event_signups_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE event_signups
    ADD CONSTRAINT fk_event_signups_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE event_sign_up_answers
    ADD CONSTRAINT fk_event_sign_up_answers_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE event_sign_up_answers
    ADD CONSTRAINT fk_event_sign_up_answers_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE files
    ADD CONSTRAINT fk_files_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE files
    ADD CONSTRAINT fk_files_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE guests
    ADD CONSTRAINT fk_guests_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE guests
    ADD CONSTRAINT fk_guests_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE memberships
    ADD CONSTRAINT fk_memberships_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE memberships
    ADD CONSTRAINT fk_memberships_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE redirects
    ADD CONSTRAINT fk_redirects_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE redirects
    ADD CONSTRAINT fk_redirects_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE sponsors
    ADD CONSTRAINT fk_sponsors_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE sponsors
    ADD CONSTRAINT fk_sponsors_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE surveys
    ADD CONSTRAINT fk_surveys_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE surveys
    ADD CONSTRAINT fk_surveys_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE telemetries
    ADD CONSTRAINT fk_telemetries_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE telemetries
    ADD CONSTRAINT fk_telemetries_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_on_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);

ALTER TABLE users
    ADD CONSTRAINT fk_users_on_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);