ALTER TABLE member_profiles
    ADD COLUMN conditions_accepted_at DATETIME NULL AFTER ehbo;

-- Creating a membership is what agreeing the terms means, so the fact is already
-- recorded as the first membership row's timestamp.
UPDATE member_profiles mp
    JOIN (SELECT user_id, MIN(created_at) AS accepted_at
          FROM memberships
          WHERE deleted_at = '9999-12-31 23:59:59'
          GROUP BY user_id) first_membership
    ON first_membership.user_id = mp.id
SET mp.conditions_accepted_at = first_membership.accepted_at;
