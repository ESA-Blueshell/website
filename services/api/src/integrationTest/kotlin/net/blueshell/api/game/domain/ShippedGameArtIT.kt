package net.blueshell.api.game.domain

import net.blueshell.api.esports.domain.ShippedEsports
import net.blueshell.api.file.api.FileService
import net.blueshell.api.file.api.ShippedPictures
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.game.persistence.GameRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.testsupport.EsportsSeedFixture
import net.blueshell.api.testsupport.GameSeedFixture
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.nio.file.Files
import java.nio.file.Paths
import javax.sql.DataSource

/**
 * The banners and icons the repository ships land on the games the seed files name.
 *
 * Run against the real converter and the real storage volume, because the point of the step is
 * that the bytes are there and are served at the widths a caller asks for. The games are the
 * ones [EsportsSeedFixture] adds, and their art is [GameSeedFixture].
 */
@SpringBootTest
class ShippedGameArtIT : UserTestSupport() {
    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var pictures: ShippedPictures

    @Autowired private lateinit var games: GameRepository

    @Autowired private lateinit var files: FileService

    @Autowired private lateinit var stored: FileRepository

    @Value($$"${storage.location}")
    private lateinit var storageLocation: String

    /** The step under test, reading the fixture seed rather than the one the site ships. */
    private val art: ShippedGameArt by lazy { ShippedGameArt(pictures, games, GameSeedFixture.files) }

    @BeforeEach
    fun loadTheGames() {
        ShippedEsports(dataSource, transactionTemplate, EsportsSeedFixture.files).apply()
    }

    /**
     * Every picture the loader puts on a record is stored at more than one width.
     *
     * The tests below name one banner and one icon. This is the guarantee itself: the boot loader is the
     * only thing that puts the shipped art in storage, so a picture it stores at one width is a picture
     * served at full size for ever. Nothing else would fail — it is drawn, the picture is right, and it is
     * simply many times the weight it should be. The ladder stops at the master's own width because nothing
     * is upscaled, so what is asserted is that there are copies and that none is wider than the picture.
     */
    @Test
    fun `every picture it stores is stored at several widths, and none wider than itself`() {
        art.apply()

        val masters =
            stored.findSourcesOfTypes(
                listOf(FileType.GAME_BANNER, FileType.GAME_ICON),
            )
        assertThat(masters).isNotEmpty

        val bare = masters.filter { it.renditions.isEmpty() }.map { it.name }.sorted()
        assertThat(bare).describedAs("shipped pictures stored at one width only").isEmpty()

        val upscaled =
            masters
                .flatMap { master ->
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

        val valorant = games.findByCode("ALPHA")
        assertThat(valorant?.banner).isNotNull
        assertThat(valorant?.banner?.type).isEqualTo(FileType.GAME_BANNER)
    }

    @Test
    fun `a game the file gives an icon to has one of its own`() {
        art.apply()

        val valorant = games.findByCode("ALPHA")
        assertThat(valorant?.icon).isNotNull
        assertThat(valorant?.icon?.type).isEqualTo(FileType.GAME_ICON)
    }

    @Test
    fun `an icon is stored at the widths an icon of its kind is served at`() {
        art.apply()

        val icon = games.findByCode("ALPHA")?.icon!!
        // The art is 256 wide, so the ladder stops there rather than inventing a 512.
        assertThat(icon.width).isEqualTo(256)
        assertThat(icon.renditions.mapNotNull { it.renditionWidth }).containsExactly(128, 256)
        assertThat(files.findPublicImage(icon.path, FileType.GAME_ICON)).isNotNull
    }

    /**
     * Every game, including the one nothing is said about.
     *
     * A logo existed in the frontend for each game, and which of them was drawn was a separate
     * question from which of them had one — some were simply never wired up. Every game the
     * files list is wired up here.
     */
    @Test
    fun `every game the file names carries an icon`() {
        art.apply()

        val without = EsportsSeedFixture.GAMES.filter { games.findByCode(it)?.icon == null }

        assertThat(without).describedAs("games the shipped icons did not reach").isEmpty()
    }

    @Test
    fun `a game keeps its own icon rather than its predecessor's`() {
        art.apply()

        // Two games a reader takes for one history are still two logos to draw.
        assertThat(games.findByCode("ALPHA")?.icon?.path)
            .isNotEqualTo(games.findByCode("BETA")?.icon?.path)
    }

    @Test
    fun `an icon somebody chose is not replaced either`() {
        art.apply()
        val chosen = games.findByCode("ALPHA")?.icon!!
        val gamma = games.findByCode("GAMMA")!!
        gamma.icon = chosen
        games.save(gamma)

        art.apply()

        assertThat(games.findByCode("GAMMA")?.icon?.path).isEqualTo(chosen.path)
    }

    @Test
    fun `an icon whose bytes have gone missing is written again at the address it had`() {
        art.apply()
        val icon = games.findByCode("ALPHA")?.icon!!
        val bytes = Paths.get(storageLocation).resolve(icon.path)
        Files.delete(bytes)

        art.apply()

        // Asked of an icon as well as a banner because the two are stored by separate steps,
        // and a volume that repairs half of itself leaves the whole site half drawn.
        assertThat(Files.exists(bytes)).isTrue()
        assertThat(games.findByCode("ALPHA")?.icon?.path).isEqualTo(icon.path)
    }

    @Test
    fun `a game banner somebody chose is not replaced either`() {
        art.apply()
        val before = games.findByCode("BETA")?.banner?.path

        art.apply()

        assertThat(games.findByCode("BETA")?.banner?.path).isEqualTo(before)
    }

    @Test
    fun `running it again changes nothing`() {
        val first = art.apply()

        val second = art.apply()

        assertThat(first.banners).isGreaterThan(0)
        assertThat(first.icons).isGreaterThan(0)
        assertThat(second).isEqualTo(ShippedGameArt.Applied(banners = 0, icons = 0))
    }

    @AfterEach
    fun forgetTheFixtureGames() {
        EsportsSeedFixture.forget(dataSource)
    }
}
