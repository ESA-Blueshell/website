CREATE TABLE surveys
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59',
    event_id   BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    KEY ix_surveys_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE questions
(
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    survey_id     BIGINT      NOT NULL,
    type          VARCHAR(16) NOT NULL,
    label         TEXT        NULL,
    choice_labels JSON        NULL,
    idx           BIGINT      NOT NULL,
    created_at    DATETIME    NOT NULL DEFAULT NOW(),
    deleted_at    DATETIME    NOT NULL DEFAULT '9999-12-31 23:59:59',
    PRIMARY KEY (id),
    KEY ix_questions_survey_deleted (survey_id, deleted_at),
    KEY ix_questions_deleted (deleted_at),
    CONSTRAINT uq_questions_survey_pos UNIQUE (survey_id, idx, deleted_at),
    CONSTRAINT fk_questions_survey
        FOREIGN KEY (survey_id) REFERENCES surveys (id)
            ON DELETE CASCADE ON UPDATE CASCADE
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE answers
(
    id                       BIGINT   NOT NULL AUTO_INCREMENT,
    question_id              BIGINT   NOT NULL,
    option_selections        JSON     NULL,
    text_response            TEXT     NULL,
    event_sign_up_answers_id BIGINT   NULL,
    created_at               DATETIME NOT NULL DEFAULT NOW(),
    deleted_at               DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59',
    PRIMARY KEY (id),
    KEY ix_answers_question_deleted (question_id, deleted_at),
    KEY ix_answers_deleted (deleted_at),
    CONSTRAINT fk_answers_question
        FOREIGN KEY (question_id) REFERENCES questions (id)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE event_sign_up_answers
(
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    answer_id         BIGINT   NULL,
    event_sign_up_id  BIGINT   NULL,
    question_id       BIGINT   NULL,
    option_selections JSON     NULL,
    text_response     TEXT     NULL,
    created_at        DATETIME NOT NULL DEFAULT NOW(),
    deleted_at        DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59',
    PRIMARY KEY (id),
    CONSTRAINT fk_event_sign_up_answers_answer
        FOREIGN KEY (answer_id) REFERENCES answers (id)
            ON DELETE CASCADE
            ON UPDATE CASCADE,
    CONSTRAINT fk_event_sign_up
        FOREIGN KEY (event_sign_up_id) REFERENCES event_signups (id)
) DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

ALTER TABLE events
    ADD COLUMN survey_id BIGINT NULL;

ALTER TABLE events
    ADD INDEX ix_events_sign_up_form_deleted (survey_id, deleted_at);

INSERT INTO surveys (deleted_at, event_id)
SELECT events.deleted_at, events.id
FROM events
WHERE events.sign_up_form IS NOT NULL;

UPDATE events
    LEFT OUTER JOIN surveys ON events.id = surveys.event_id
SET events.survey_id = surveys.id
WHERE events.id = surveys.event_id;

ALTER TABLE surveys
    DROP COLUMN event_id;

INSERT INTO questions (survey_id, type, label, idx, deleted_at, choice_labels)
WITH RECURSIVE
    base AS (SELECT e.id,
                    e.survey_id,
                    e.deleted_at,
                    e.sign_up_form,
                    JSON_LENGTH(e.sign_up_form) AS n
             FROM events e),
    idx AS (SELECT id, survey_id, deleted_at, sign_up_form, 0 AS idx, n
            FROM base
            UNION ALL
            SELECT id, survey_id, deleted_at, sign_up_form, idx + 1, n
            FROM idx
            WHERE idx + 1 < n)
SELECT i.survey_id,
       UPPER(JSON_UNQUOTE(JSON_EXTRACT(i.sign_up_form, CONCAT('$[', i.idx, '].type')))) AS type,
       JSON_UNQUOTE(JSON_EXTRACT(i.sign_up_form, CONCAT('$[', i.idx, '].prompt')))      AS label,
       i.idx,
       i.deleted_at,
       JSON_UNQUOTE(JSON_EXTRACT(i.sign_up_form, CONCAT('$[', i.idx, '].options')))     AS choice_labels
FROM idx i
         JOIN events e ON e.id = i.id
WHERE e.survey_id IS NOT NUll;

ALTER TABLE questions
    MODIFY COLUMN survey_id BIGINT NOT NULL,
    MODIFY COLUMN type VARCHAR(16) NOT NULL,
    MODIFY COLUMN idx INT NOT NULL,
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59';

-- Insert one row per (signup x question) into event_sign_up_answers
-- - Skips DESCRIPTION (no answer)
-- - For OPEN:   text_response = answer text, option_selections = NULL
-- - For RADIO/CHECKBOX: text_response = NULL,
--       option_selections = JSON array of booleans (length = #options)
--       true at selected indices, false elsewhere.

INSERT INTO event_sign_up_answers
(deleted_at, event_sign_up_id, question_id, option_selections, text_response)
WITH RECURSIVE
    base AS (SELECT es.id           AS event_sign_up_id,
                    es.event_id     AS event_id,
                    es.deleted_at   AS deleted_at,
                    es.form_answers AS form_answers,
                    e.survey_id     AS survey_id,
                    e.sign_up_form  AS sign_up_form
             FROM event_signups es
                      JOIN events e ON e.id = es.event_id
             WHERE e.sign_up_form IS NOT NULL
               AND e.survey_id IS NOT NULL),
    qidx AS (SELECT b.event_id,
                    b.deleted_at,
                    b.event_sign_up_id,
                    b.survey_id,
                    b.form_answers,
                    b.sign_up_form,
                    0                           AS idx,
                    JSON_LENGTH(b.sign_up_form) AS n
             FROM base b
             UNION ALL
             SELECT q.event_id,
                    q.deleted_at,
                    q.event_sign_up_id,
                    q.survey_id,
                    q.form_answers,
                    q.sign_up_form,
                    q.idx + 1,
                    q.n
             FROM qidx q
             WHERE q.idx + 1 < q.n),
    jq AS (SELECT qx.event_id,
                  qx.deleted_at,
                  qx.event_sign_up_id,
                  qx.survey_id,
                  qx.idx,
                  qu.id                                                    AS question_id,
                  qu.type                                                  AS question_type,
                  qu.choice_labels,
                  JSON_EXTRACT(qx.form_answers, CONCAT('$[', qx.idx, ']')) AS raw_answer
           FROM qidx qx
                    JOIN questions qu
                         ON qu.survey_id = qx.survey_id
                             AND qu.idx = qx.idx
           WHERE qu.type IN ('OPEN', 'RADIO', 'CHECKBOX')),
    open_rows AS (SELECT event_id,
                         deleted_at,
                         event_sign_up_id,
                         survey_id,
                         question_id,
                         NULL                     AS option_selections,
                         JSON_UNQUOTE(raw_answer) AS text_response
                  FROM jq
                  WHERE question_type = 'OPEN'),
    opt_indices AS (SELECT j.event_id,
                           j.deleted_at,
                           j.event_sign_up_id,
                           j.survey_id,
                           j.question_id,
                           j.raw_answer,
                           j.choice_labels,
                           0                            AS opt_idx,
                           JSON_LENGTH(j.choice_labels) AS m
                    FROM jq j
                    WHERE j.question_type IN ('RADIO', 'CHECKBOX')
                    UNION ALL
                    SELECT o.event_id,
                           o.deleted_at,
                           o.event_sign_up_id,
                           o.survey_id,
                           o.question_id,
                           o.raw_answer,
                           o.choice_labels,
                           o.opt_idx + 1,
                           o.m
                    FROM opt_indices o
                    WHERE o.opt_idx + 1 < o.m),
    mcq_rows AS (SELECT o.event_id,
                        o.deleted_at,
                        o.event_sign_up_id,
                        o.survey_id,
                        o.question_id,
                        JSON_ARRAYAGG(
                                JSON_EXTRACT(
                                        IF(
                                                COALESCE(
                                                        JSON_CONTAINS(
                                                                CASE
                                                                    WHEN JSON_TYPE(o.raw_answer) = 'ARRAY'
                                                                        THEN o.raw_answer
                                                                    WHEN JSON_TYPE(o.raw_answer) IN ('INTEGER', 'DOUBLE')
                                                                        THEN JSON_ARRAY(o.raw_answer)
                                                                    ELSE JSON_ARRAY()
                                                                    END,
                                                                o.opt_idx
                                                        ), 0
                                                ) = 1,
                                                'true', 'false'
                                        ),
                                        '$'
                                )
                                ORDER BY o.opt_idx
                        )    AS option_selections,
                        NULL AS text_response
                 FROM opt_indices o
                 GROUP BY o.event_id, o.deleted_at, o.event_sign_up_id, o.survey_id, o.question_id)
SELECT deleted_at, event_sign_up_id, question_id, option_selections, text_response
FROM open_rows
UNION ALL
SELECT deleted_at, event_sign_up_id, question_id, option_selections, text_response
FROM mcq_rows;

INSERT INTO answers (deleted_at, question_id, option_selections, text_response, event_sign_up_answers_id)
SELECT esa.deleted_at, esa.question_id, esa.option_selections, esa.text_response, esa.id as event_sign_up_answers_id
FROM event_sign_up_answers AS esa
         LEFT JOIN answers a ON a.event_sign_up_answers_id = esa.id
WHERE a.id IS NULL;

UPDATE event_sign_up_answers AS esa
    JOIN answers a
    ON a.event_sign_up_answers_id = esa.id
SET esa.answer_id = a.id
WHERE esa.answer_id IS NULL;

ALTER TABLE event_sign_up_answers
    MODIFY COLUMN event_sign_up_id BIGINT NOT NULL,
    MODIFY COLUMN answer_id BIGINT NOT NULL,
    DROP question_id,
    DROP option_selections,
    DROP text_response;

ALTER TABLE answers
    MODIFY COLUMN question_id BIGINT NOT NULL,
    MODIFY COLUMN deleted_at DATETIME NOT NULL DEFAULT '9999-12-31 23:59:59',
    DROP event_sign_up_answers_id;

ALTER TABLE events
    DROP sign_up_form;

ALTER TABLE event_signups
    DROP form_answers;

ALTER TABLE events
    ADD CONSTRAINT fk_events_survey
        FOREIGN KEY (survey_id) REFERENCES surveys (id)
            ON DELETE SET NULL
            ON UPDATE CASCADE;

ALTER TABLE questions
    DROP INDEX ix_questions_survey_deleted,
    ADD KEY ix_questions_survey_deleted_idx (survey_id, deleted_at, idx);

ALTER TABLE event_sign_up_answers
    ADD KEY ix_esa_deleted (deleted_at),
    ADD KEY ix_esa_event_deleted (event_sign_up_id, deleted_at),
    ADD UNIQUE KEY uq_esa_answer (answer_id);

ALTER TABLE questions
    MODIFY COLUMN survey_id BIGINT NOT NULL;

ALTER TABLE answers
    MODIFY COLUMN question_id BIGINT NOT NULL;

ALTER TABLE event_sign_up_answers
    MODIFY COLUMN event_sign_up_id BIGINT NOT NULL,
    MODIFY COLUMN answer_id BIGINT NOT NULL;