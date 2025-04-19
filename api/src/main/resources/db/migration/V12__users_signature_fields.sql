ALTER TABLE users
    ADD COLUMN signature_id BIGINT,
    ADD COLUMN signature_date DATE,
    ADD COLUMN signature_city VARCHAR(255);

ALTER TABLE users
    ADD CONSTRAINT fk_signature_id
        FOREIGN KEY (signature_id)
            REFERENCES pictures (id);
