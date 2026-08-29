package net.blueshell.api.esports.domain

import db.migration.R__Esports_seed
import net.blueshell.api.esports.persistence.EsportsBannerRepository
import net.blueshell.api.esports.persistence.TeamRepository
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
 * that the bytes are there and are served at the widths the pages ask for. What a picture is
 * worth is not assertable; that a team has one, that it is the one the file named and that a
 * second start does not take it away are.
 */
@SpringBootTest
class ShippedArtIT : UserTestSupport() {

    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var art: ShippedArt

    @Autowired private lateinit var teams: TeamRepository

    @Autowired private lateinit var banners: EsportsBannerRepository

    @Autowired private lateinit var files: FileService

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

    @Test
    fun `a team the file gives art to has a poster`() {
        art.apply()

        val team = teams.findByGameAndNameIgnoreCase("VALORANT", "BS Huge")
        assertThat(team?.poster).isNotNull
        assertThat(team?.poster?.type).isEqualTo(FileType.TEAM_POSTER)
    }

    @Test
    fun `a poster is stored at the widths a picture of its kind is served at`() {
        art.apply()

        val poster = teams.findByGameAndNameIgnoreCase("VALORANT", "BS Huge")?.poster!!
        // The art is 2560 wide, which is every width the kind lists.
        assertThat(poster.width).isEqualTo(2560)
        assertThat(files.findPublicImage(poster.path, FileType.TEAM_POSTER)).isNotNull
    }

    @Test
    fun `a game the file gives art to has a banner set for the game as a whole`() {
        art.apply()

        val forGame = banners.findAllByGame("VALORANT").filter { it.seasonId == null && it.teamId == null }
        assertThat(forGame).hasSize(1)
        assertThat(forGame.single().file.type).isEqualTo(FileType.ESPORTS_BANNER)
    }

    @Test
    fun `two teams given the same picture share one stored file`() {
        art.apply()

        // Rocket League fields more teams than it has art, so a picture carries two of them.
        val oogway = teams.findByGameAndNameIgnoreCase("ROCKET_LEAGUE", "BS Oogway")?.poster
        val turtles = teams.findByGameAndNameIgnoreCase("ROCKET_LEAGUE", "BS Turtles")?.poster
        assertThat(oogway?.path).isEqualTo(turtles?.path)
    }

    @Test
    fun `running it again changes nothing`() {
        val first = art.apply()

        val second = art.apply()

        assertThat(first.posters).isGreaterThan(0)
        assertThat(first.banners).isGreaterThan(0)
        assertThat(second).isEqualTo(ShippedArt.Applied(posters = 0, banners = 0))
    }

    @Test
    fun `bytes that have gone missing are written again at the address they had`() {
        art.apply()
        val poster = teams.findByGameAndNameIgnoreCase("VALORANT", "BS Huge")?.poster!!
        val bytes = Paths.get(storageLocation).resolve(poster.path)
        Files.delete(bytes)

        art.apply()

        // A lost storage volume repairs itself rather than invalidating every url anybody
        // cached, which only holds because the art is stored whether or not a slot wants it.
        assertThat(Files.exists(bytes)).isTrue()
        assertThat(teams.findByGameAndNameIgnoreCase("VALORANT", "BS Huge")?.poster?.path).isEqualTo(poster.path)
    }

    @Test
    fun `a poster somebody chose is not replaced by the one the file names`() {
        val chosen = files.store(
            content = ByteArrayInputStream(pngOf()),
            originalName = "chosen.png",
            declaredMediaType = "image/png",
            type = FileType.TEAM_POSTER,
            uploader = createUserWithRole(Role.ADMIN),
        )
        val team = teams.findByGameAndNameIgnoreCase("VALORANT", "BS Huge")!!
        team.poster = chosen
        teams.save(team)

        art.apply()

        // An admin's choice is later than the import, so the import leaves it alone.
        assertThat(teams.findByGameAndNameIgnoreCase("VALORANT", "BS Huge")?.poster?.path).isEqualTo(chosen.path)
    }

    @Test
    fun `a banner somebody set for the game is not replaced`() {
        art.apply()
        val before = banners.findAllByGame("CS2").single { it.seasonId == null && it.teamId == null }.file.path

        art.apply()

        assertThat(banners.findAllByGame("CS2").single { it.seasonId == null && it.teamId == null }.file.path)
            .isEqualTo(before)
    }

    private fun pngOf(): ByteArray {
        val image = java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB)
        return java.io.ByteArrayOutputStream().also { javax.imageio.ImageIO.write(image, "png", it) }.toByteArray()
    }
}
