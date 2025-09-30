CREATE UNIQUE INDEX uk_users_username ON users (username, deleted_at);
CREATE UNIQUE INDEX uk_users_email ON users (email, deleted_at);
CREATE UNIQUE INDEX uk_users_phone_number ON users (phone_number, deleted_at);
CREATE UNIQUE INDEX uk_users_discord ON users (discord, deleted_at);