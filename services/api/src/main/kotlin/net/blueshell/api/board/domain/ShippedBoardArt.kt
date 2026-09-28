package net.blueshell.api.board.domain

import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.file.api.ShippedPictures
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.seed.SeedOrder
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Puts the photographs the `photo` and `portrait` columns name on each board and member that
 * has none.
 *
 * One stored picture backs at most one record: `picture_id` is unique on both tables and storage
 * is content-addressed, so two rows naming one art file would contend for one `File`
 * (`ShippedBoardArtFilesTest` fails the build if they ever do). A board or member the files name
 * and the database does not is skipped: the seed leaves a removed record removed.
 */
@Component
class ShippedBoardArt(
    private val pictures: ShippedPictures,
    private val boards: BoardRepository,
    private val members: BoardMemberRepository,
) {
    /** The pictures a run put on records, which is none at all on every start after the first. */
    data class Applied(
        val photos: Int,
        val portraits: Int,
    )

    @Order(SeedOrder.ART)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        apply()
    }

    fun apply(): Applied =
        Applied(
            photos = pictures.ship(BoardSeed.files, "boards.csv", "photo", FileType.BOARD_PHOTO, ::photo),
            portraits = pictures.ship(BoardSeed.files, "members.csv", "portrait", FileType.BOARD_PORTRAIT, ::portrait),
        )

    private fun photo(
        row: Map<String, String>,
        picture: () -> File,
    ): Boolean {
        val board = boards.findByNumber(row.getValue("number").toInt()).orElse(null) ?: return false
        if (board.picture != null) return false
        val photo = picture()
        val holder = boards.findByPictureId(photo.id!!).orElse(null)
        if (holder != null && holder.id != board.id) return held(photo, "board ${holder.number}")
        board.replacePicture(photo)
        boards.save(board)
        return true
    }

    /**
     * The member is found by the board they sat on and the name recorded for them, which is
     * exactly the key the seed writes them under, so a member whose recorded name was corrected
     * is skipped.
     */
    private fun portrait(
        row: Map<String, String>,
        picture: () -> File,
    ): Boolean {
        val board = boards.findByNumber(row.getValue("board").toInt()).orElse(null) ?: return false
        val record = members.findByBoardId(board.id!!).firstOrNull { it.displayName == row.getValue("name") } ?: return false
        if (record.picture != null) return false
        val portrait = picture()
        val holder = members.findByPictureId(portrait.id!!).orElse(null)
        if (holder != null && holder.id != record.id) return held(portrait, "the membership of ${holder.displayName}")
        record.replacePicture(portrait)
        members.save(record)
        return true
    }

    private fun held(
        picture: File,
        holder: String,
    ): Boolean {
        log.warn("[shipped-board-art] {} already belongs to {}, so it is left there", picture.name, holder)
        return false
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedBoardArt::class.java)
    }
}
