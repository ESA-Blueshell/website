-- The code that wrote `contacts` and `contact_external_ids` is gone: the id
-- each external system knows a person by lives in `external_id_mapping`, which
-- V58 backfilled from here and `sync` has written ever since.
--
-- The rows are kept, not dropped. `contact_list_memberships` holds an FK into
-- `contacts`, and V67 retains that table on purpose as the cohort-cutover
-- rollback snapshot — so `contacts` cannot go before it does. All five tables
-- drop together, once the cohort path has been observed long enough that the
-- rollback snapshot is no longer wanted.

ALTER TABLE contacts
    COMMENT = 'Frozen snapshot. Nothing reads or writes it; external ids live in external_id_mapping. Drop together with the contact_list* cohort-cutover snapshots.';
ALTER TABLE contact_external_ids
    COMMENT = 'Frozen snapshot. Superseded by external_id_mapping (aggregate_type=USER), backfilled in V58. Drop together with the contact_list* cohort-cutover snapshots.';
