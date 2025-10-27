DELETE m
FROM memberships m
         JOIN users u ON u.id = m.user_id
WHERE u.email IN ('us@er', 'ad@min', 'lou@uis');

DELETE u
FROM users u
WHERE u.email IN ('us@er', 'ad@min', 'lou@uis');

CREATE UNIQUE INDEX uk_users_username ON users (username, deleted_at);
CREATE UNIQUE INDEX uk_users_email ON users (email, deleted_at);
CREATE UNIQUE INDEX uk_users_phone_number ON users (phone_number, deleted_at);
CREATE UNIQUE INDEX uk_users_discord ON users (discord, deleted_at);