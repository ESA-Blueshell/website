-- ── contacts ─────────────────────────────────────────────────────────────────
-- Create a Contact for each user that already has a Brevo contact_id
INSERT INTO contacts (user_id, synced_email, version, deleted_at)
SELECT id, IFNULL(email, ''), 0, '9999-12-31 23:59:59.000000'
FROM users
WHERE contact_id IS NOT NULL
  AND deleted_at = '9999-12-31 23:59:59.000000';

-- Store the existing Brevo contact ID in brevo_contacts
INSERT INTO brevo_contacts (contact_id, external_id)
SELECT cr.id, u.contact_id
FROM contacts cr
JOIN users u ON u.id = cr.user_id
WHERE u.contact_id IS NOT NULL;

-- ── lists ─────────────────────────────────────────────────────────────────────
-- Build a de-duplicated set of list names from contribution periods
INSERT INTO contact_lists (name, version, deleted_at)
SELECT DISTINCT
    CONCAT('Contribution Paid ', YEAR(start_date), ' - ', YEAR(end_date)),
    0,
    '9999-12-31 23:59:59.000000'
FROM contribution_periods
WHERE list_id IS NOT NULL
  AND deleted_at = '9999-12-31 23:59:59.000000';

-- Store the existing Brevo list ID in brevo_lists
INSERT INTO brevo_lists (list_id, external_id)
SELECT cl.id, cp.list_id
FROM contact_lists cl
JOIN contribution_periods cp
  ON cl.name = CONCAT('Contribution Paid ', YEAR(cp.start_date), ' - ', YEAR(cp.end_date))
WHERE cp.list_id IS NOT NULL
  AND cp.deleted_at = '9999-12-31 23:59:59.000000';

-- ── contribution_periods ──────────────────────────────────────────────────────
-- Add FK column pointing to the new contact_lists table
ALTER TABLE contribution_periods ADD COLUMN contact_list_id BIGINT;

UPDATE contribution_periods cp
JOIN contact_lists cl
  ON cl.name = CONCAT('Contribution Paid ', YEAR(cp.start_date), ' - ', YEAR(cp.end_date))
  AND cl.deleted_at = '9999-12-31 23:59:59.000000'
SET cp.contact_list_id = cl.id
WHERE cp.list_id IS NOT NULL;
