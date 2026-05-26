-- Retire Listmonk as a sync target.
--
-- The api no longer ships a Listmonk transactional client or a Listmonk
-- contact-list integration; SMTP via Stalwart is the only outbound transport
-- and Brevo is the only contact target. Drop every persisted row tagged
-- LISTMONK so the enum can be removed from code.

DELETE FROM external_id_mapping WHERE system = 'LISTMONK';
DELETE FROM contact_external_ids WHERE system = 'LISTMONK';
DELETE FROM contact_list_external_ids WHERE system = 'LISTMONK';
