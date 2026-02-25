-- Remove redundant boolean flags from deleted_users snapshot.
-- addressId is retained; hadMemberProfile and hadAddress are derivable via query.
ALTER TABLE deleted_users
    DROP COLUMN had_member_profile,
    DROP COLUMN had_address;

-- Remove dead-code consent_gdpr column from users.
-- Field was never read or written in business logic.
ALTER TABLE users DROP COLUMN consent_gdpr;
