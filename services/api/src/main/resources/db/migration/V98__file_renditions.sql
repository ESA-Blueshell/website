-- The narrower copies a picture is stored at.
--
-- A visitor on a phone downloads the same picture as somebody on a desktop, because there is
-- only ever one of it. Each width becomes a file record of its own, pointing at the picture it
-- was derived from, so the same route serves it and the same rules decide who may read it.
--
-- `rendition_width` is what the copy claims to be, and it is also half of its address: a width
-- is stored under its source's hash and its own number rather than under a hash of its own
-- bytes. That is what lets a width whose bytes have gone missing be written again to the
-- address somebody is already holding, and what lets the converter be upgraded without
-- changing bytes at an address that promised never to change.
--
-- Both columns are null for a picture that was uploaded, which is every row that exists today.

ALTER TABLE files
    ADD COLUMN source_file_id  BIGINT NULL AFTER height,
    ADD COLUMN rendition_width INT    NULL AFTER source_file_id,
    ADD CONSTRAINT fk_files_source FOREIGN KEY (source_file_id) REFERENCES files (id);

-- The widths of one picture, which is how a payload reads them and how the derivation asks
-- what is already there.
CREATE INDEX idx_files_source ON files (source_file_id, rendition_width);
