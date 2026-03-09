CREATE TABLE contact_external_ids (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contact_id  BIGINT      NOT NULL,
    system      VARCHAR(50) NOT NULL,
    external_id BIGINT      NOT NULL,
    CONSTRAINT uk_contact_external_ids_contact_system UNIQUE (contact_id, system),
    CONSTRAINT fk_contact_external_ids_contact FOREIGN KEY (contact_id) REFERENCES contacts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE contact_list_external_ids (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contact_list_id BIGINT      NOT NULL,
    system          VARCHAR(50) NOT NULL,
    external_id     BIGINT      NOT NULL,
    CONSTRAINT uk_contact_list_external_ids_list_system UNIQUE (contact_list_id, system),
    CONSTRAINT fk_contact_list_external_ids_list FOREIGN KEY (contact_list_id) REFERENCES contact_lists(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO contact_external_ids (contact_id, system, external_id)
    SELECT contact_id, 'LISTMONK', external_id FROM listmonk_contacts;

INSERT INTO contact_external_ids (contact_id, system, external_id)
    SELECT contact_id, 'BREVO', external_id FROM brevo_contacts;

INSERT INTO contact_list_external_ids (contact_list_id, system, external_id)
    SELECT list_id, 'LISTMONK', external_id FROM listmonk_lists;

INSERT INTO contact_list_external_ids (contact_list_id, system, external_id)
    SELECT list_id, 'BREVO', external_id FROM brevo_lists;

DROP TABLE listmonk_contacts;
DROP TABLE brevo_contacts;
DROP TABLE listmonk_lists;
DROP TABLE brevo_lists;
