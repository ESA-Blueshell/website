ALTER TABLE users MODIFY COLUMN date_of_birth datetime;
ALTER TABLE users ADD COLUMN gender varchar(255);
ALTER TABLE users ADD COLUMN street varchar(255);
ALTER TABLE users ADD COLUMN country varchar(255);
ALTER TABLE users ADD COLUMN photoConsent bool;
ALTER TABLE users ADD COLUMN nationality varchar(255);
ALTER TABLE users ADD COLUMN study varchar(255);
ALTER TABLE users ADD COLUMN startStudyYear int;