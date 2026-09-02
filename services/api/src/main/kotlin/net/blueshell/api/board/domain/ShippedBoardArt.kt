package net.blueshell.api.board.domain

import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Puts the photographs the repository ships onto the boards and members the seed files name.
 *
 * The pictures under `db/seed/boards/art` are the association's own record of its own boards.
 * The `photo` column of `boards.csv` says which board each group photograph belongs to and the
 * `portrait` column of the members file says which member each portrait belongs to. Every picture is
 * stored the way an upload is — converted, addressed by its contents, written at the ladder of
 * widths its kind lists — and credited to the site's own account, because nobody chose it.
 *
 * Here rather than in the repeatable migration that loads those same files. Storing a picture
 * needs the storage volume and the converter, and a migration runner has neither; it also runs
 * before the application is up, which is to say before there is anywhere to put the bytes. So
 * the migration writes the records and this puts the art on them, in that order, on every
 * start. It is the esports loader's twin and every rule below is inherited from it.
 *
 * **A picture already chosen is never replaced.** A slot that is filled is somebody's decision
 * and it is later than this one, so only an empty slot is written. Correcting the photograph of
 * a board that already has one is therefore an edit made through the api rather than in the
 * file — the same rule the seed's own header states for a board or a member that was removed.
 *
 * Storing and applying are separate, and every picture is stored on every start whether or not
 * anything is waiting for it. That is what makes a lost storage volume repair itself: the bytes
 * go back to the address they always had, so a url that has been served for a year answers
 * again. Skipping the ones nothing is waiting for would be faster and would mean the pictures
 * somebody chose are exactly the ones that never come back.
 *
 * **One stored picture backs at most one record.** `boards` and `board_members` each carry a
 * unique key on `picture_id`, and storage is content-addressed, so two rows naming one art file
 * would resolve to one `File` row that only one of them could hold. No two rows do — the build
 * fails if they ever did, see `ShippedBoardArtFilesTest` — and where a picture is already held
 * by another record this leaves it there and says so, rather than letting a unique key answer
 * for it. A board's photograph has no fielding to belong to the way a team's banner does, so
 * there is no version of this where a picture is legitimately shared.
 */
@Component
class ShippedBoardArt(
    private val files: FileService,
    private val users: UserService,
    private val boards: BoardRepository,
    private val members: BoardMemberRepository,
    private val transactions: TransactionTemplate,
) {
    /** The pictures a run put on records, which is none at all on every start after the first. */
    data class Applied(val photos: Int, val portraits: Int)

    /** One member, as the files identify it: the board they sat on and the name recorded for them. */
    private data class Member(val board: Int, val name: String)

    fun apply(): Applied {
        val owner = siteAccount() ?: return Applied(0, 0)
        val photos = BoardSeed.files.rows(BOARDS)
            .mapNotNull { row ->
                row[PHOTO]?.ifBlank { null }?.let { art -> row.getValue(NUMBER).toInt() to art }
            }
        val portraits = BoardSeed.files.rows(MEMBERS)
            .mapNotNull { row ->
                row[PORTRAIT]?.ifBlank { null }?.let { art ->
                    Member(row.getValue(BOARD).toInt(), row.getValue(NAME)) to art
                }
            }

        // Every picture first, so one that is waiting for nothing is still put back where it
        // was. The addresses remembered here are what stops a picture being stored twice.
        val stored = mutableMapOf<Pair<String, FileType>, String>()
        photos.forEach { (number, art) ->
            attempt("the photograph for board $number") { store(art, FileType.BOARD_PHOTO, owner, stored); 0 }
        }
        portraits.forEach { (member, art) ->
            attempt("the portrait for ${member.name}") { store(art, FileType.BOARD_PORTRAIT, owner, stored); 0 }
        }

        val photosDrawn = photos.sumOf { (number, art) ->
            attempt("the photograph of board $number") { photo(number, art, owner, stored) }
        }
        val portraitsDrawn = portraits.sumOf { (member, art) ->
            attempt("the portrait of ${member.name}") { portrait(member, art, owner, stored) }
        }

        if (photosDrawn > 0 || portraitsDrawn > 0) {
            log.info(
                "[shipped-board-art] {} board photographs and {} portraits now come from the art that ships",
                photosDrawn,
                portraitsDrawn,
            )
        }
        return Applied(photos = photosDrawn, portraits = portraitsDrawn)
    }

    /**
     * The board's own group photograph, where the board has none.
     *
     * A board the file names and the database does not is not an error here: the seed leaves a
     * board that was removed removed, and its row stays in the file until somebody takes it out.
     */
    private fun photo(
        number: Int,
        art: String,
        owner: User,
        stored: MutableMap<Pair<String, FileType>, String>,
    ): Int = transactions.execute {
        val board = boards.findByNumber(number).orElse(null) ?: return@execute 0
        if (board.picture != null) return@execute 0
        val picture = store(art, FileType.BOARD_PHOTO, owner, stored)
        val holder = boards.findByPictureId(picture.id!!).orElse(null)
        if (holder != null && holder.id != board.id) {
            return@execute held(art, "board ${holder.number}")
        }
        board.replacePicture(picture)
        boards.save(board)
        1
    }

    /**
     * The member's own portrait, where the member has none.
     *
     * The member is found by the board they sat on and the name recorded for them, which is
     * exactly the key the seed writes them under. A member that was removed, or one whose
     * recorded name was corrected, simply is not there and is left alone.
     */
    private fun portrait(
        member: Member,
        art: String,
        owner: User,
        stored: MutableMap<Pair<String, FileType>, String>,
    ): Int = transactions.execute {
        val board = boards.findByNumber(member.board).orElse(null) ?: return@execute 0
        val record = members.findByBoardId(board.id!!).firstOrNull { it.displayName == member.name }
            ?: return@execute 0
        if (record.picture != null) return@execute 0
        val picture = store(art, FileType.BOARD_PORTRAIT, owner, stored)
        val holder = members.findByPictureId(picture.id!!).orElse(null)
        if (holder != null && holder.id != record.id) {
            return@execute held(art, "the membership of ${holder.displayName}")
        }
        record.replacePicture(picture)
        members.save(record)
        1
    }

    /** A picture another record already holds, which is a picture this one cannot have. */
    private fun held(art: String, holder: String): Int {
        log.warn("[shipped-board-art] {} already belongs to {}, so it is left there", art, holder)
        return 0
    }

    /**
     * One picture, stored the first time it is asked for.
     *
     * What is remembered between records is the address rather than the row, because each of
     * them is written in a transaction of its own and a row read in one is stale in the next.
     * The address is stable — it is the picture's own contents — so a second ask reads back the
     * row the first one wrote instead of storing the bytes again.
     */
    private fun store(
        art: String,
        kind: FileType,
        owner: User,
        stored: MutableMap<Pair<String, FileType>, String>,
    ): File {
        stored[art to kind]?.let { path -> files.findPublicImage(path, kind)?.let { return it } }
        val name = "$art.webp"
        val resource = "${BoardSeed.files.directory}/art/$name"
        val bytes = javaClass.classLoader.getResourceAsStream(resource)
            ?: error("Shipped board art $resource is missing")
        val file = files.store(bytes, name, WEBP, kind, owner)
        stored[art to kind] = file.path
        return file
    }

    /**
     * The account the shipped art is credited to.
     *
     * Absent only where the migration that writes it has not run, which is to say never in a
     * running application. Answering with null rather than throwing keeps that impossible case
     * from being the reason a start fails.
     */
    private fun siteAccount(): User? {
        val account = runCatching { users.findByUsername(SITE_ACCOUNT) }.getOrNull()
        if (account == null) {
            log.warn("[shipped-board-art] there is no '{}' account to credit the art to", SITE_ACCOUNT)
        }
        return account
    }

    /** One record's art, whose failure is its own rather than the rest of the run's. */
    private fun attempt(what: String, apply: () -> Int): Int =
        try {
            apply()
        } catch (e: Exception) {
            log.warn("[shipped-board-art] could not set {}: {}", what, e.message)
            0
        }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedBoardArt::class.java)
        const val BOARDS = "boards.csv"
        const val MEMBERS = "members.csv"
        const val NUMBER = "number"
        const val BOARD = "board"
        const val NAME = "name"
        const val PHOTO = "photo"
        const val PORTRAIT = "portrait"
        const val WEBP = "image/webp"
        const val SITE_ACCOUNT = "system"
    }
}

/**
 * Applies the shipped board art once the application is up, and never stops it coming up.
 *
 * A separate bean so the work is reached through the proxy, the way the esports loader beside
 * it is arranged.
 *
 * A failure is reported and swallowed. Art that did not land leaves a record pointing at the
 * picture it pointed at yesterday, and refusing to start over one would take the whole site
 * down — which is the exact failure this art is meant to decorate, not cause.
 */
@Component
class ShippedBoardArtOnStartup(
    private val art: ShippedBoardArt,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        try {
            art.apply()
        } catch (e: Exception) {
            log.warn("[shipped-board-art] could not put the art that ships onto the boards: {}", e.message)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedBoardArtOnStartup::class.java)
    }
}
