ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS answer_count BIGINT UNSIGNED NOT NULL DEFAULT 0;

ALTER TABLE surveys
    ADD COLUMN IF NOT EXISTS response_count BIGINT UNSIGNED NOT NULL DEFAULT 0;

ALTER TABLE events
    ADD COLUMN IF NOT EXISTS sign_up_count BIGINT UNSIGNED NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS ix_answers_qid_deleted
    ON answers (question_id, deleted_at);

CREATE INDEX IF NOT EXISTS ix_questions_survey_deleted_type_count
    ON questions (survey_id, deleted_at, type, answer_count);

CREATE INDEX IF NOT EXISTS ix_event_signups_event_deleted
    ON event_signups (event_id, deleted_at);


UPDATE questions q
SET q.answer_count = (SELECT COUNT(*)
                      FROM answers a
                      WHERE a.question_id = q.id
                        AND a.deleted_at = '9999-12-31 23:59:59');

UPDATE surveys s
SET s.response_count = COALESCE((SELECT MAX(q.answer_count)
                                 FROM questions q
                                 WHERE q.survey_id = s.id
                                   AND q.deleted_at = '9999-12-31 23:59:59'
                                   AND q.type <> 'DESCRIPTION'), 0);

UPDATE events e
SET e.sign_up_count = (SELECT COUNT(*)
                       FROM event_signups es
                       WHERE es.event_id = e.id
                         AND es.deleted_at = '9999-12-31 23:59:59');

DROP TRIGGER IF EXISTS trg_answers_ai;
DROP TRIGGER IF EXISTS trg_answers_au;
DROP TRIGGER IF EXISTS trg_answers_ad;

DROP TRIGGER IF EXISTS trg_questions_ai;
DROP TRIGGER IF EXISTS trg_questions_au;
DROP TRIGGER IF EXISTS trg_questions_ad;

DROP TRIGGER IF EXISTS trg_event_signups_ai;
DROP TRIGGER IF EXISTS trg_event_signups_au;
DROP TRIGGER IF EXISTS trg_event_signups_ad;

DELIMITER $$

CREATE TRIGGER trg_answers_ai
    AFTER INSERT
    ON answers
    FOR EACH ROW
BEGIN
    IF NEW.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE questions
        SET answer_count = answer_count + 1
        WHERE id = NEW.question_id;
    END IF;
END$$

CREATE TRIGGER trg_answers_au
    AFTER UPDATE
    ON answers
    FOR EACH ROW
BEGIN
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
END$$

CREATE TRIGGER trg_answers_ad
    AFTER DELETE
    ON answers
    FOR EACH ROW
BEGIN
    IF OLD.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE questions
        SET answer_count = GREATEST(answer_count - 1, 0)
        WHERE id = OLD.question_id;
    END IF;
END$$

CREATE TRIGGER trg_questions_ai
    AFTER INSERT
    ON questions
    FOR EACH ROW
BEGIN
    IF NEW.deleted_at = '9999-12-31 23:59:59' AND NEW.type <> 'DESCRIPTION' THEN
        UPDATE surveys s
        SET s.response_count = GREATEST(s.response_count, NEW.answer_count)
        WHERE s.id = NEW.survey_id;
    END IF;
END$$

CREATE TRIGGER trg_questions_au
    AFTER UPDATE
    ON questions
    FOR EACH ROW
BEGIN
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
END$$

CREATE TRIGGER trg_questions_ad
    AFTER DELETE
    ON questions
    FOR EACH ROW
BEGIN
    IF OLD.deleted_at = '9999-12-31 23:59:59' AND OLD.type <> 'DESCRIPTION' THEN
        UPDATE surveys s
        SET s.response_count = COALESCE((SELECT MAX(q.answer_count)
                                         FROM questions q
                                         WHERE q.survey_id = OLD.survey_id
                                           AND q.deleted_at = '9999-12-31 23:59:59'
                                           AND q.type <> 'DESCRIPTION'), 0)
        WHERE s.id = OLD.survey_id;
    END IF;
END$$

CREATE TRIGGER trg_event_signups_ai
    AFTER INSERT
    ON event_signups
    FOR EACH ROW
BEGIN
    IF NEW.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE events
        SET sign_up_count = sign_up_count + 1
        WHERE id = NEW.event_id;
    END IF;
END$$

CREATE TRIGGER trg_event_signups_au
    AFTER UPDATE
    ON event_signups
    FOR EACH ROW
BEGIN
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
END$$

CREATE TRIGGER trg_event_signups_ad
    AFTER DELETE
    ON event_signups
    FOR EACH ROW
BEGIN
    IF OLD.deleted_at = '9999-12-31 23:59:59' THEN
        UPDATE events
        SET sign_up_count = GREATEST(sign_up_count - 1, 0)
        WHERE id = OLD.event_id;
    END IF;
END$$

DELIMITER ;
