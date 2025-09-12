ALTER TABLE users ADD COLUMN reset_key varchar(255);
ALTER TABLE users ADD COLUMN reset_key_valid_until datetime;
ALTER TABLE users ADD COLUMN reset_type varchar(255);
