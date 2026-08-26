-- The outbox kept only what an email was addressed to and what happened to it, never what it
-- said, so a sent email could not be read back. Store the body a domain produced, in the
-- markdown it produced it in: it is a fraction of the size of the rendered html, carries no
-- tracking pixel, and re-renders through the same template the send path uses.
--
-- Nullable on purpose. Rows written before this column existed have no body to show, and the
-- api tells the reader that rather than inventing one.
ALTER TABLE emails
    ADD COLUMN body_markdown LONGTEXT NULL;
