-- The account that owns the files the site ships with.
--
-- Every file record names whoever uploaded it, and the art that ships with the repository was
-- uploaded by nobody. Crediting a board member with art they never chose would put a person's
-- name on a decision they did not make, so an account exists to hold it instead.
--
-- It cannot sign in. `enabled` is false and the password is a single character, which is not a
-- BCrypt hash and therefore matches nothing the encoder is ever handed. Neither is the reason
-- it cannot sign in: the authentication path refuses the SYSTEM role outright, whatever else is
-- true of the account, so a password reset cannot turn it into a live administrator.
--
-- The role inherits administrator. That is deliberate: an account that owns records has to be
-- allowed to hold them, and giving it the role means it carries the role's mitigations rather
-- than being a quiet exception nobody remembers to check.
--
-- It is not a member. It holds no membership, appears in no user listing and is left out of the
-- contact fan-out, so it never reaches the mailing list.

INSERT INTO users (username, password, first_name, last_name, initials, email,
                   newsletter, enabled, photo_consent, consent_privacy)
VALUES ('system', '!', 'Blue', 'Shell', 'BS', 'system@esa-blueshell.nl',
        0, b'0', 0, b'0');

-- The row just written rather than a lookup by name: a person could already be called that,
-- and the account this migration means is the one it inserted.
INSERT INTO authorities (user_id, authority)
VALUES (LAST_INSERT_ID(), 'SYSTEM');
