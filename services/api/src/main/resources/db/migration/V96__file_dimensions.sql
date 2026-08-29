-- What size an image actually is, recorded when it is stored.
--
-- The pages draw an uploaded image without knowing anything about it, so a browser cannot
-- reserve its space and the layout jumps as each one arrives. They are also about to be stored
-- at several widths, and which widths apply to a picture depends on how wide the picture is.
--
-- Nullable, and null is a real answer rather than a missing one: a stored file whose format
-- nothing here can read still belongs in the table, and refusing an upload because its size
-- could not be measured would be a worse trade than drawing it without one.

ALTER TABLE files
    ADD COLUMN width  INT NULL AFTER size,
    ADD COLUMN height INT NULL AFTER width;
