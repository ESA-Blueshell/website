CREATE TABLE member_profiles
(
    id              BIGINT       NOT NULL PRIMARY KEY,
    date_of_birth   DATE         NULL,
    student_number  VARCHAR(255) NULL,
    gender          VARCHAR(64)  NULL,
    photo_consent   BIT(1)       NULL,
    nationality     VARCHAR(128) NULL,
    bhv             BIT(1)       NULL,
    ehbo            BIT(1)       NULL,
    deleted_at      DATETIME     NOT NULL DEFAULT '9999-12-31 23:59:59',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_id   BIGINT       NULL,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_id   BIGINT       NULL,
    version         BIGINT       NOT NULL DEFAULT 0
);

ALTER TABLE member_profiles
    ADD CONSTRAINT fk_member_profiles_id FOREIGN KEY (id) REFERENCES users (id);
ALTER TABLE member_profiles
    ADD CONSTRAINT fk_member_profiles_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);
ALTER TABLE member_profiles
    ADD CONSTRAINT fk_member_profiles_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

CREATE TABLE study_programs
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    level          VARCHAR(16)  NOT NULL,
    name           VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL,
    active         BIT(1)       NOT NULL DEFAULT b'1',
    deleted_at     DATETIME     NOT NULL DEFAULT '9999-12-31 23:59:59',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_id  BIGINT       NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_id  BIGINT       NULL,
    version        BIGINT       NOT NULL DEFAULT 0
);

ALTER TABLE study_programs
    ADD CONSTRAINT fk_study_programs_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);
ALTER TABLE study_programs
    ADD CONSTRAINT fk_study_programs_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

CREATE UNIQUE INDEX uk_study_programs_name_level_deleted_at ON study_programs (name, level, deleted_at);
CREATE INDEX idx_study_programs_level_active ON study_programs (level, active);

CREATE TABLE user_studies
(
    id                             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT      NOT NULL,
    study_program_id               BIGINT      NOT NULL,
    status                         VARCHAR(16) NOT NULL,
    start_year                     INT         NULL,
    graduation_year                INT         NULL,
    deleted_at                     DATETIME    NOT NULL DEFAULT '9999-12-31 23:59:59',
    created_at                     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_id                  BIGINT      NULL,
    updated_at                     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_id                  BIGINT      NULL,
    version                        BIGINT      NOT NULL DEFAULT 0
);

ALTER TABLE user_studies
    ADD CONSTRAINT fk_user_studies_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_studies
    ADD CONSTRAINT fk_user_studies_program FOREIGN KEY (study_program_id) REFERENCES study_programs (id);
ALTER TABLE user_studies
    ADD CONSTRAINT fk_user_studies_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);
ALTER TABLE user_studies
    ADD CONSTRAINT fk_user_studies_updated_by FOREIGN KEY (updated_by_id) REFERENCES users (id);

CREATE INDEX idx_user_studies_user_id ON user_studies (user_id);
CREATE INDEX idx_user_studies_status ON user_studies (status);

INSERT INTO study_programs (level, name, active)
VALUES ('BSC', 'Applied Computer Science', b'1'),
       ('BSC', 'Computer Science', b'1'),
       ('BSC', 'Electrical Engineering', b'1'),
       ('MSC', 'Computer Science', b'1'),
       ('MSC', 'Embedded Systems', b'1'),
       ('MSC', 'Electrical Engineering', b'1');

INSERT INTO study_programs (level, name, active)
SELECT 'BSC', u.study, b'1'
FROM users u
WHERE u.study IS NOT NULL
  AND TRIM(u.study) <> ''
  AND NOT EXISTS(
        SELECT 1
        FROM study_programs sp
        WHERE sp.name COLLATE utf8mb4_unicode_ci = u.study
          AND sp.level = 'BSC'
          AND sp.deleted_at = '9999-12-31 23:59:59'
    );

INSERT INTO member_profiles (
    id,
    date_of_birth,
    student_number,
    gender,
    photo_consent,
    nationality,
    bhv,
    ehbo,
    deleted_at,
    created_at,
    created_by_id,
    updated_at,
    updated_by_id,
    version
)
SELECT u.id,
       u.date_of_birth,
       u.student_number,
       u.gender,
       u.photo_consent,
       u.nationality,
       u.bhv,
       u.ehbo,
       u.deleted_at,
       u.created_at,
       u.created_by_id,
       u.updated_at,
       u.updated_by_id,
       u.version
FROM users u
WHERE u.date_of_birth IS NOT NULL
   OR u.student_number IS NOT NULL
   OR u.gender IS NOT NULL
   OR u.nationality IS NOT NULL
   OR u.photo_consent IS NOT NULL
   OR u.bhv IS NOT NULL
   OR u.ehbo IS NOT NULL
   OR u.study IS NOT NULL
   OR u.start_study_year IS NOT NULL;

INSERT INTO user_studies (
    user_id,
    study_program_id,
    status,
    start_year,
    graduation_year,
    deleted_at,
    created_at,
    created_by_id,
    updated_at,
    updated_by_id,
    version
)
SELECT u.id,
       sp.id,
       CASE
           WHEN u.start_study_year IS NULL THEN 'COMPLETED'
           ELSE 'ONGOING'
           END AS status,
       CAST(u.start_study_year AS SIGNED),
       NULL,
       u.deleted_at,
       u.created_at,
       u.created_by_id,
       u.updated_at,
       u.updated_by_id,
       u.version
FROM users u
         JOIN study_programs sp ON sp.name COLLATE utf8mb4_unicode_ci = u.study
             AND sp.level = 'BSC'
             AND sp.deleted_at = '9999-12-31 23:59:59'
WHERE (u.study IS NOT NULL AND TRIM(u.study) <> '')
   OR u.start_study_year IS NOT NULL;

ALTER TABLE users
    DROP COLUMN date_of_birth,
    DROP COLUMN student_number,
    DROP COLUMN gender,
    DROP COLUMN photo_consent,
    DROP COLUMN nationality,
    DROP COLUMN bhv,
    DROP COLUMN ehbo,
    DROP COLUMN study,
    DROP COLUMN start_study_year;
