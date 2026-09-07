-- The asset file name of a board's photograph, and of a seat's portrait, go.

-- Both columns named a file in the frontend's own source tree. They were the whole of a board
-- picture until V87 put `picture_id` beside them, and they were kept through the expand half of
-- that migration so the page drawing them kept working. The page now reads the stored picture,
-- the management editor that wrote them is gone, and the seed carries `photo` and `portrait`
-- instead — the art it ships is stored by `ShippedBoardArt` on start.
--
-- Nothing to move across first: every seeded row that named an asset names shipped art too, and
-- an asset file name is a path, not a picture, so there is nothing here the file service wants.

ALTER TABLE boards
    DROP COLUMN image;

ALTER TABLE board_members
    DROP COLUMN image;
