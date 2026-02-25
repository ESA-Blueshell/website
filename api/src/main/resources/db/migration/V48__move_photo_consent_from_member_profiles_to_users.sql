-- Move photo_consent from member_profiles to users
ALTER TABLE users ADD COLUMN photo_consent BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users u JOIN member_profiles mp ON u.id = mp.id SET u.photo_consent = mp.photo_consent;
ALTER TABLE member_profiles DROP COLUMN photo_consent;

-- Also add photo_consent to deleted_users snapshot table
ALTER TABLE deleted_users ADD COLUMN photo_consent BOOLEAN NOT NULL DEFAULT FALSE;
