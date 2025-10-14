insert into authorities (user_id, authority)
SELECT users.id, 'GUEST'
FROM users
WHERE users.id NOT IN (SELECT user_id FROM authorities WHERE authority = 'GUEST');