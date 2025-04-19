ALTER TABLE event_signups
    ADD COLUMN form_answers_temp JSON;

UPDATE event_signups
SET form_answers_temp =
        CASE
            WHEN JSON_VALID(form_answers) THEN form_answers
            ELSE NULL
            END;

ALTER TABLE event_signups
DROP COLUMN form_answers;

ALTER TABLE event_signups
    CHANGE COLUMN form_answers_temp form_answers JSON;
