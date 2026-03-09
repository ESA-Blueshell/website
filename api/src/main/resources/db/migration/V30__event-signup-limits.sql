ALTER TABLE events
  ADD COLUMN sign_up_deadline DATETIME NULL,
  ADD COLUMN sign_up_limit    INT      NULL;
