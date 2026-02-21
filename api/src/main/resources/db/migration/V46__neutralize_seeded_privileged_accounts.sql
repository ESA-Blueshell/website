-- Concern(s): remove/neutralize seeded privileged bootstrap accounts from V0 without breaking existing DBs
-- Safety: all statements are no-op if target seeded accounts do not exist
-- Targeted accounts:
--   - admin (seeded with known bcrypt hash)
--   - board (seeded with known bcrypt hash)

/* =========================
   (2) Data – remove authorities for seeded privileged accounts
   ========================= */
DELETE a
FROM authorities a
         INNER JOIN users u ON u.id = a.user_id
WHERE (u.username = 'admin' AND u.password = '$2a$10$cwKSYweW60.FIJf8rR40.e8t3706g4ReEDEXAYmxX16oXkWfdVSba')
   OR (u.username = 'board' AND u.password = '$2a$10$/qL7UwPKq0qeAoQDrQ2k2egdk7ldDroa50CPNmf6nud7F4QOGm3S6');

/* =========================
   (2) Data – disable and soft-delete seeded privileged accounts
   ========================= */
UPDATE users u
SET u.enabled    = FALSE,
    u.newsletter = FALSE,
    u.updated_at = NOW(),
    u.deleted_at = NOW(),
    u.version    = u.version + 1
WHERE (u.username = 'admin' AND u.password = '$2a$10$cwKSYweW60.FIJf8rR40.e8t3706g4ReEDEXAYmxX16oXkWfdVSba')
   OR (u.username = 'board' AND u.password = '$2a$10$/qL7UwPKq0qeAoQDrQ2k2egdk7ldDroa50CPNmf6nud7F4QOGm3S6');
