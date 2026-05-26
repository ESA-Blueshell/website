-- Widen external_id_mapping.external_id to fit long Google Calendar event IDs.
--
-- The original V58 column was VARCHAR(255). Recurring-event exception ids
-- encoded by Google can exceed that (267 chars observed in prod), which made
-- V58 fail mid-transaction on the events backfill INSERT and crash-looped the
-- api for ~12 h until the column was widened by hand. VARCHAR(1024) matches
-- Google's documented upper bound; external_id is not part of any index, so
-- the length change has no key-prefix consequences.

ALTER TABLE external_id_mapping MODIFY COLUMN external_id VARCHAR(1024) NULL;
