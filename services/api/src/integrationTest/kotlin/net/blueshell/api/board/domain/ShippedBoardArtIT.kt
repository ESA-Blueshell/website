package net.blueshell.api.board.domain

import db.migration.R__Boards_seed
import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.board.persistence.BoardRepository
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.api.migration.Context
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.Connection
import javax.sql.DataSource

/**
 * The photographs the repository ships land on the boards and seats the seed files name.
 *
 * Run against the real converter and the real storage volume, because the point of the step is
 * that the bytes are there and are served at the widths a caller asks for. What a photograph is
 * worth is not assertable; that board 9 has one, that it is the one the file named and that a
 * second start does not take it away are.
 */
@SpringBootTest
class ShippedBoardArtIT : UserTestSupport() {

    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var art: ShippedBoardArt

    @Autowired private lateinit var boards: BoardRepository

    @Autowired private lateinit var seats: BoardMemberRepository

    @Autowired private lateinit var files: FileService

    @Autowired private lateinit var stored: FileRepository

    @Value($$"${storage.location}")
    private lateinit var storageLocation: String

    @BeforeEach
    fun loadTheRecords() {
        dataSource.connection.use { connection ->
            R__Boards_seed().migrate(object : Context {
                override fun getConfiguration() = null
                override fun getConnection(): Connection = connection
            })
        }
    }

    /** One board's photograph, read where its widths can still be read. */
    private fun photoOf(number: Int): Picture? = transactionTemplate.execute {
        boards.findByNumber(number).orElse(null)?.picture?.let(::snapshot)
    }

    /** One seat's portrait, found the way the loader finds the seat: its board and its name. */
    private fun portraitOf(board: Int, name: String): Picture? = transactionTemplate.execute {
        val id = boards.findByNumber(board).orElse(null)?.id ?: return@execute null
        seats.findByBoardId(id).firstOrNull { it.displayName == name }?.picture?.let(::snapshot)
    }

    private fun snapshot(file: File) = Picture(
        path = file.path,
        type = file.type,
        width = file.width,
        widths = file.renditions.mapNotNull { it.renditionWidth },
    )

    private data class Picture(
        val path: String,
        val type: FileType,
        val width: Int?,
        val widths: List<Int>,
    )

    @Test
    fun `a board the file gives a photograph to has one`() {
        art.apply()

        val photo = photoOf(9)
        assertThat(photo).isNotNull
        assertThat(photo?.type).isEqualTo(FileType.BOARD_PHOTO)
    }

    @Test
    fun `a photograph is stored at the widths a picture of its kind is served at`() {
        art.apply()

        val photo = photoOf(9)!!
        // The shipped photograph of board 9 is 1300 wide, so the ladder stops below 1920.
        assertThat(photo.width).isEqualTo(1300)
        assertThat(photo.widths).containsExactly(320, 640, 960, 1280)
    }

    @Test
    fun `a seat the file gives a portrait to has one, at the widths a portrait is served at`() {
        art.apply()

        val portrait = portraitOf(6, "Amber Scholtz")!!
        assertThat(portrait.type).isEqualTo(FileType.BOARD_PORTRAIT)
        // Fitted to 960 on its longest edge, which for a 2:3 photograph is 640 across.
        assertThat(portrait.width).isEqualTo(640)
        assertThat(portrait.widths).containsExactly(160, 320, 640)
    }

    /**
     * Fifteen of the twenty-one portraits are 600 wide in the repository, so their ladder
     * stops at 320. That is the art's limit rather than the kind's, and it is asserted so that
     * a rescanned photograph showing up as three widths reads as an improvement rather than a
     * surprise.
     */
    @Test
    fun `a portrait narrower than the ladder is stored at the widths it can fill`() {
        art.apply()

        val portrait = portraitOf(9, "Emma Dokter")!!
        assertThat(portrait.width).isEqualTo(600)
        assertThat(portrait.widths).containsExactly(160, 320)
    }

    /**
     * Every picture the loader puts on a record is stored at more than one width.
     *
     * The tests above name one photograph and two portraits. This is the guarantee itself: the
     * boot loader is the only thing that puts the shipped art in storage, so a picture it
     * stores at one width is a picture served at full size for ever. Nothing else would fail —
     * it is drawn, the picture is right, and it is simply many times the weight it should be.
     */
    @Test
    fun `every picture it stores is stored at several widths, and none wider than itself`() {
        art.apply()

        val masters = transactionTemplate.execute {
            stored.findSourcesOfTypes(listOf(FileType.BOARD_PHOTO, FileType.BOARD_PORTRAIT))
                .map { it.name to snapshot(it) }
        }!!
        assertThat(masters).hasSize(26)

        val bare = masters.filter { it.second.widths.isEmpty() }.map { it.first }.sorted()
        assertThat(bare).describedAs("shipped pictures stored at one width only").isEmpty()

        val upscaled = masters.flatMap { (name, picture) ->
            picture.widths
                .filter { width -> picture.width?.let { width > it } ?: false }
                .map { "$name at ${it}px, wider than ${picture.width}" }
        }.sorted()
        assertThat(upscaled).describedAs("copies wider than the picture they came from").isEmpty()
    }

    @Test
    fun `the five boards and twenty-one seats the files name are the ones that get art`() {
        val applied = art.apply()

        assertThat(applied).isEqualTo(ShippedBoardArt.Applied(photos = 5, portraits = 21))
        // The four boards nobody photographed and the candidate board that has not sat.
        assertThat(listOf(1, 2, 3, 4, 10).mapNotNull { photoOf(it) })
            .describedAs("boards given a photograph the files do not name")
            .isEmpty()
        // Board 5 has five seats and no portraits of any of them.
        assertThat(portraitOf(5, "Daniël Floor")).isNull()
    }

    @Test
    fun `running it again changes nothing`() {
        val first = art.apply()

        val second = art.apply()

        assertThat(first.photos).isGreaterThan(0)
        assertThat(first.portraits).isGreaterThan(0)
        assertThat(second).isEqualTo(ShippedBoardArt.Applied(photos = 0, portraits = 0))
    }

    @Test
    fun `bytes that have gone missing are written again at the address they had`() {
        art.apply()
        val photo = photoOf(9)!!
        val bytes = Paths.get(storageLocation).resolve(photo.path)
        Files.delete(bytes)

        art.apply()

        // A lost storage volume repairs itself rather than invalidating every url anybody
        // cached, which only holds because the art is stored whether or not a slot wants it.
        assertThat(Files.exists(bytes)).isTrue()
        assertThat(photoOf(9)?.path).isEqualTo(photo.path)
    }

    @Test
    fun `a portrait whose bytes have gone missing is written again at the address it had`() {
        art.apply()
        val portrait = portraitOf(6, "Amber Scholtz")!!
        val bytes = Paths.get(storageLocation).resolve(portrait.path)
        Files.delete(bytes)

        art.apply()

        // Asked of a portrait as well as a photograph because the two are stored by separate
        // steps, and a volume that repairs half of itself leaves the page half drawn.
        assertThat(Files.exists(bytes)).isTrue()
        assertThat(portraitOf(6, "Amber Scholtz")?.path).isEqualTo(portrait.path)
    }

    @Test
    fun `a photograph somebody chose is not replaced by the one the file names`() {
        val chosen = files.store(
            content = ByteArrayInputStream(pngOf()),
            originalName = "chosen.png",
            declaredMediaType = "image/png",
            type = FileType.BOARD_PHOTO,
            uploader = createUserWithRole(Role.ADMIN),
        )
        transactionTemplate.execute {
            val board = boards.findByNumber(9).orElseThrow()
            board.replacePicture(chosen)
            boards.save(board)
        }

        art.apply()

        // A board's own choice is later than the import, so the import leaves it alone.
        assertThat(photoOf(9)?.path).isEqualTo(chosen.path)
    }

    @Test
    fun `a portrait somebody chose is not replaced either`() {
        val chosen = files.store(
            content = ByteArrayInputStream(pngOf()),
            originalName = "chosen.png",
            declaredMediaType = "image/png",
            type = FileType.BOARD_PORTRAIT,
            uploader = createUserWithRole(Role.ADMIN),
        )
        transactionTemplate.execute {
            val id = boards.findByNumber(6).orElseThrow().id!!
            val seat = seats.findByBoardId(id).first { it.displayName == "Amber Scholtz" }
            seat.replacePicture(chosen)
            seats.save(seat)
        }

        art.apply()

        assertThat(portraitOf(6, "Amber Scholtz")?.path).isEqualTo(chosen.path)
    }

    /**
     * A picture another board already holds is left where it is.
     *
     * `uk_boards_picture_deleted_at` says a stored picture backs one board, and storage is
     * content-addressed, so this is what would happen if two rows ever named one file. The
     * files do not — `ShippedBoardArtFilesTest` fails the build if they did — but the loader
     * says so in a log line and carries on rather than letting a unique key answer with a
     * stack trace half way through a start.
     */
    @Test
    fun `a photograph another board already holds is left there`() {
        art.apply()
        val ninth = photoOf(9)!!
        transactionTemplate.execute {
            val ninthBoard = boards.findByNumber(9).orElseThrow()
            ninthBoard.replacePicture(null)
            boards.save(ninthBoard)
        }
        transactionTemplate.execute {
            val eighth = boards.findByNumber(8).orElseThrow()
            eighth.replacePicture(stored.findByPath(ninth.path).orElseThrow())
            boards.save(eighth)
        }

        val applied = art.apply()

        assertThat(applied.photos).describedAs("boards the run gave a photograph").isEqualTo(0)
        assertThat(photoOf(9)).describedAs("board 9, whose photograph board 8 is holding").isNull()
        assertThat(photoOf(8)?.path).isEqualTo(ninth.path)
    }

    private fun pngOf(): ByteArray {
        val image = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
        return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "png", it) }.toByteArray()
    }
}
