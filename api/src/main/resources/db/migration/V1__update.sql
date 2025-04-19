-- Add end_time and google_id columns and set their default values
alter table events
add end_time timestamp null;

alter table events
add google_id text null;


ALTER DATABASE API CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
ALTER TABLE events CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE events CHANGE description description text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;