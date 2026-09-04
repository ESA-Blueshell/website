package net.blueshell.api.esports.domain

import db.migration.R__Esports_seed
import net.blueshell.api.esports.persistence.GameRepository
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamSeason
import net.blueshell.api.esports.persistence.TeamSeasonRepository
import net.blueshell.api.file.persistence.File
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.file.api.FileService
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
 * The art the repository ships lands on the records the seed files name.
 *
 * Run against the real converter and the real storage volume, because the point of the step is
 * that the bytes are there and are served at the widths a caller asks for. What a picture is
 * worth is not assertable; that a team has one, that it is the one the file named and that a
 * second start does not take it away are.
 */
@SpringBootTest
class ShippedArtIT : UserTestSupport() {

    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var art: ShippedArt

    @Autowired private lateinit var teams: TeamRepository

    @Autowired private lateinit var fielded: TeamSeasonRepository

    @Autowired private lateinit var games: GameRepository

    @Autowired private lateinit var files: FileService

    @Autowired private lateinit var stored: FileRepository

    @Value($$"${storage.location}")
    private lateinit var storageLocation: String

    @BeforeEach
    fun loadTheRecords() {
        dataSource.connection.use { connection ->
            R__Esports_seed().migrate(object : Context {
                override fun getConfiguration() = null
                override fun getConnection(): Connection = connection
            })
        }
    }

    /**
     * The art a team is drawn with in one game, which belongs to the fielding rather than to
     * the team: the same team plays other games and is drawn differently in each.
     */
    private fun bannerOf(game: String, name: String): File? =
        teams.findByNameIgnoreCase(name)
            ?.id
            ?.let { id -> fielded.findAllByTeamId(id).firstOrNull { it.game == game }?.banner }

    private fun fieldingOf(game: String, name: String): TeamSeason =
        fielded.findAllByTeamId(teams.findByNameIgnoreCase(name)!!.id!!).first { it.game == game }

    @Test
    fun `a team the file gives art to has a banner`() {
        art.apply()

        val banner = bannerOf("VALORANT", "BS Huge")
        assertThat(banner).isNotNull
        assertThat(banner?.type).isEqualTo(FileType.TEAM_BANNER)
    }

    @Test
    fun `a banner is stored at the widths a picture of its kind is served at`() {
        art.apply()

        val banner = bannerOf("VALORANT", "BS Huge")!!
        // The art is 2560 wide, which is every width the kind lists.
        assertThat(banner.width).isEqualTo(2560)
        assertThat(files.findPublicImage(banner.path, FileType.TEAM_BANNER)).isNotNull
        // The widths themselves, which this test only claimed to check before: a master with
        // no copies beside it is one a caller falls back to at full size, on every screen.
        assertThat(banner.renditions.mapNotNull { it.renditionWidth })
            .containsExactly(320, 640, 960, 1280, 1920, 2560)
    }

    /**
     * Every picture the loader puts on a record is stored at more than one width.
     *
     * The two tests above name one banner and one icon. This is the guarantee itself: the boot loader is the
     * only thing that puts the shipped art in storage, so a picture it stores at one width is a picture
     * served at full size for ever. Nothing else would fail — it is drawn, the picture is right, and it is
     * simply many times the weight it should be. The ladder stops at the master's own width because nothing
     * is upscaled, so what is asserted is that there are copies and that none is wider than the picture.
     */
    @Test
    fun `every picture it stores is stored at several widths, and none wider than itself`() {
        art.apply()

        val masters = stored.findSourcesOfTypes(
            listOf(FileType.TEAM_BANNER, FileType.GAME_BANNER, FileType.GAME_ICON),
        )
        assertThat(masters).isNotEmpty

        val bare = masters.filter { it.renditions.isEmpty() }.map { it.name }.sorted()
        assertThat(bare).describedAs("shipped pictures stored at one width only").isEmpty()

        val upscaled = masters.flatMap { master ->
            master.renditions
                .mapNotNull { it.renditionWidth }
                .filter { width -> master.width?.let { width > it } ?: false }
                .map { "${master.name} at ${it}px, wider than ${master.width}" }
        }.sorted()
        assertThat(upscaled).describedAs("copies wider than the picture they came from").isEmpty()
    }

    @Test
    fun `a game the file gives art to has a banner of its own`() {
        art.apply()

        val valorant = games.findByCode("VALORANT")
        assertThat(valorant?.banner).isNotNull
        assertThat(valorant?.banner?.type).isEqualTo(FileType.GAME_BANNER)
    }

    @Test
    fun `a game the file gives an icon to has one of its own`() {
        art.apply()

        val valorant = games.findByCode("VALORANT")
        assertThat(valorant?.icon).isNotNull
        assertThat(valorant?.icon?.type).isEqualTo(FileType.GAME_ICON)
    }

    @Test
    fun `an icon is stored at the widths an icon of its kind is served at`() {
        art.apply()

        val icon = games.findByCode("VALORANT")?.icon!!
        // The art is 256 wide, so the ladder stops there rather than inventing a 512.
        assertThat(icon.width).isEqualTo(256)
        assertThat(icon.renditions.mapNotNull { it.renditionWidth }).containsExactly(128, 256)
        assertThat(files.findPublicImage(icon.path, FileType.GAME_ICON)).isNotNull
    }

    /**
     * Every game, including the two whose teams are history.
     *
     * A logo existed in the frontend for all eight, and which of them was drawn was a
     * separate question from which of them had one — the game record named a file for six of
     * them and the other two were simply never wired up. All eight are wired up here.
     */
    @Test
    fun `every game the file names carries an icon`() {
        art.apply()

        val without = listOf(
            "VALORANT", "CS2", "LEAGUE_OF_LEGENDS", "ROCKET_LEAGUE",
            "GEOGUESSR", "TRACKMANIA", "CSGO", "SMASH",
        ).filter { games.findByCode(it)?.icon == null }

        assertThat(without).describedAs("games the shipped icons did not reach").isEmpty()
    }

    @Test
    fun `a game keeps its own icon rather than its predecessor's`() {
        art.apply()

        // CS:GO and CS2 are one history to a reader and two logos to draw.
        assertThat(games.findByCode("CSGO")?.icon?.path)
            .isNotEqualTo(games.findByCode("CS2")?.icon?.path)
    }

    @Test
    fun `an icon somebody chose is not replaced either`() {
        art.apply()
        val chosen = games.findByCode("VALORANT")?.icon!!
        val geoguessr = games.findByCode("GEOGUESSR")!!
        geoguessr.icon = chosen
        games.save(geoguessr)

        art.apply()

        assertThat(games.findByCode("GEOGUESSR")?.icon?.path).isEqualTo(chosen.path)
    }

    @Test
    fun `two teams given the same picture share one stored file`() {
        art.apply()

        // Rocket League fields more teams than it has art, so a picture carries two of them.
        val oogway = bannerOf("ROCKET_LEAGUE", "BS Oogway")
        val turtles = bannerOf("ROCKET_LEAGUE", "BS Turtles")
        assertThat(oogway?.path).isEqualTo(turtles?.path)
    }

    @Test
    fun `running it again changes nothing`() {
        val first = art.apply()

        val second = art.apply()

        assertThat(first.teamPictures).isGreaterThan(0)
        assertThat(first.gamePictures).isGreaterThan(0)
        assertThat(second).isEqualTo(ShippedArt.Applied(teamPictures = 0, gamePictures = 0))
    }

    @Test
    fun `bytes that have gone missing are written again at the address they had`() {
        art.apply()
        val banner = bannerOf("VALORANT", "BS Huge")!!
        val bytes = Paths.get(storageLocation).resolve(banner.path)
        Files.delete(bytes)

        art.apply()

        // A lost storage volume repairs itself rather than invalidating every url anybody
        // cached, which only holds because the art is stored whether or not a slot wants it.
        assertThat(Files.exists(bytes)).isTrue()
        assertThat(bannerOf("VALORANT", "BS Huge")?.path).isEqualTo(banner.path)
    }

    @Test
    fun `an icon whose bytes have gone missing is written again at the address it had`() {
        art.apply()
        val icon = games.findByCode("VALORANT")?.icon!!
        val bytes = Paths.get(storageLocation).resolve(icon.path)
        Files.delete(bytes)

        art.apply()

        // Asked of an icon as well as a banner because the two are stored by separate steps,
        // and a volume that repairs half of itself leaves the whole site half drawn.
        assertThat(Files.exists(bytes)).isTrue()
        assertThat(games.findByCode("VALORANT")?.icon?.path).isEqualTo(icon.path)
    }

    @Test
    fun `a banner somebody chose is not replaced by the one the file names`() {
        val chosen = files.store(
            content = ByteArrayInputStream(pngOf()),
            originalName = "chosen.png",
            declaredMediaType = "image/png",
            type = FileType.TEAM_BANNER,
            uploader = createUserWithRole(Role.ADMIN),
        )
        val fielding = fieldingOf("VALORANT", "BS Huge")
        fielding.banner = chosen
        fielded.save(fielding)

        art.apply()

        // An admin's choice is later than the import, so the import leaves it alone.
        assertThat(bannerOf("VALORANT", "BS Huge")?.path).isEqualTo(chosen.path)
    }

    @Test
    fun `a game banner somebody chose is not replaced either`() {
        art.apply()
        val before = games.findByCode("CS2")?.banner?.path

        art.apply()

        assertThat(games.findByCode("CS2")?.banner?.path).isEqualTo(before)
    }

    private fun pngOf(): ByteArray {
        val image = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
        return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "png", it) }.toByteArray()
    }
}
