DELETE cm
FROM committee_members AS cm
         LEFT JOIN users AS u ON u.id = cm.user_id
WHERE u.id IS NULL;
