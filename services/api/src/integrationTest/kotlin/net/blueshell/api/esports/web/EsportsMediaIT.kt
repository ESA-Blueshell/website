package net.blueshell.api.esports.web

import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.persistence.EsportsBannerRepository
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.SeasonRepository
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.file.api.PublicFileUrls
import net.blueshell.api.file.persistence.FileRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.awt.image.BufferedImage
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Uploading, replacing and removing the images the esports pages draw, and what the public
 * payloads say about them afterwards.
 */
@SpringBootTest
class EsportsMediaIT : UserTestSupport() {
    @Autowired
    private lateinit var seasons: SeasonRepository

    @Autowired
    private lateinit var teams: TeamRepository

    @Autowired
    private lateinit var entries: TeamRosterEntryRepository

    @Autowired
    private lateinit var banners: EsportsBannerRepository

    @Autowired
    private lateinit var fielded: TeamSeasonService

    @Autowired
    private lateinit var fileRepository: FileRepository


    /** A one-pixel PNG: the smallest thing that is genuinely the content type it claims. */
    private val pngBytes = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    private fun png(name: String, bytes: ByteArray = pngBytes) =
        MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, bytes)

    private fun jpeg(name: String, width: Int, height: Int) =
        MockMultipartFile("file", name, MediaType.IMAGE_JPEG_VALUE, imageOf(width, height, "jpg"))

    private fun imageOf(
        width: Int,
        height: Int,
        format: String,
        type: Int = BufferedImage.TYPE_INT_RGB,
    ): ByteArray =
        java.io.ByteArrayOutputStream().also { out ->
            val image = BufferedImage(width, height, type)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    image.setRGB(x, y, if ((x + y) % 2 == 0) 0xff336699.toInt() else 0xffe6f0ff.toInt())
                }
            }
            ImageIO.write(image, format, out)
        }.toByteArray()

    private fun transparentPng(): ByteArray =
        java.io.ByteArrayOutputStream().also { out ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            for (x in 0 until 16) {
                for (y in 0 until 16) {
                    val alpha = if (x == y) 0x80 else 0xff
                    image.setRGB(x, y, alpha.shl(24) or 0x00_33_cc_66)
                }
            }
            ImageIO.write(image, "png", out)
        }.toByteArray()

    private fun webpOf(width: Int, height: Int): ByteArray {
        val source = java.nio.file.Files.createTempFile("esports-source-", ".png")
        val webp = java.nio.file.Files.createTempFile("esports-source-", ".webp")
        try {
            java.nio.file.Files.write(source, imageOf(width, height, "png"))
            runImageCommand(listOf("cwebp", "-quiet", source.toString(), "-o", webp.toString()))
            return java.nio.file.Files.readAllBytes(webp)
        } finally {
            java.nio.file.Files.deleteIfExists(source)
            java.nio.file.Files.deleteIfExists(webp)
        }
    }

    private fun decodedWebp(bytes: ByteArray): BufferedImage {
        val source = java.nio.file.Files.createTempFile("esports-served-", ".webp")
        val png = java.nio.file.Files.createTempFile("esports-served-", ".png")
        try {
            java.nio.file.Files.write(source, bytes)
            runImageCommand(listOf("dwebp", "-quiet", source.toString(), "-o", png.toString()))
            return ImageIO.read(png.toFile()) ?: throw AssertionError("dwebp produced a file ImageIO could not read")
        } finally {
            java.nio.file.Files.deleteIfExists(source)
            java.nio.file.Files.deleteIfExists(png)
        }
    }

    private fun runImageCommand(command: List<String>) {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        org.assertj.core.api.Assertions.assertThat(finished).describedAs(output).isTrue()
        org.assertj.core.api.Assertions.assertThat(process.exitValue()).describedAs(output).isEqualTo(0)
    }

    private fun season(name: String, year: Int): Season = seasons.save(
        Season(
            name = name,
            startDate = LocalDate.of(year, 9, 1),
            endDate = LocalDate.of(year + 1, 6, 30),
        ),
    )

    private fun team(game: String, name: String): Team = teams.save(Team(game = game, name = name))

    private fun entry(team: Team, season: Season, handle: String): TeamRosterEntry {
        fielded.field(team.id!!, season.id!!)
        return entries.save(
            TeamRosterEntry(team = team, season = season, handle = handle, teamRole = TeamRole.PLAYER),
        )
    }

    /**
     * A picture put into storage through the one endpoint that stores them, answering where it
     * went. Nothing is on a record yet: what a picture ends up on is decided by the save that
     * names it.
     */
    private fun upload(admin: User, type: FileType, file: MockMultipartFile): String {
        val result = mvc.perform(
            multipart(PublicFileUrls.UPLOAD).file(file).param("type", type.name)
                .with(bearer(admin)).with(csrfToken()),
        ).andExpect(status().isCreated).andReturn()
        return mapper.readTree(result.response.contentAsString)["path"].asText()
    }

    /** The team as it now stands, poster included — the save the dialog's Save makes. */
    private fun saveTeam(admin: User, team: Team, poster: String?): ResultActions =
        mvc.perform(
            put("/esports/teams/${team.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(mapOf("name" to team.name, "poster" to poster)))
                .with(bearer(admin)).with(csrfToken()),
        )

    /** A poster stored and then committed, answering where it is served. */
    private fun posterOn(admin: User, team: Team, file: MockMultipartFile): String {
        val stored = upload(admin, FileType.TEAM_POSTER, file)
        val saved = saveTeam(admin, team, stored).andExpect(status().isOk).andReturn()
        return mapper.readTree(saved.response.contentAsString)["poster"]["url"].asText()
    }

    /** The roster entry as it now stands, picture included. */
    private fun saveEntry(admin: User, entry: TeamRosterEntry, icon: String?): ResultActions =
        mvc.perform(
            put("/esports/roster/${entry.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf(
                            "handle" to entry.handle,
                            "role" to entry.teamRole.name,
                            "sortIndex" to entry.sortIndex,
                            "icon" to icon,
                        ),
                    ),
                )
                .with(bearer(admin)).with(csrfToken()),
        )

    /** A banner stored and then put behind a page, answering where it is served. */
    private fun bannerOn(
        admin: User,
        game: String,
        seasonId: Long?,
        teamId: Long?,
        file: MockMultipartFile,
    ): String {
        val stored = upload(admin, FileType.ESPORTS_BANNER, file)
        val result = setBanner(admin, game, seasonId, teamId, stored).andExpect(status().isOk).andReturn()
        return mapper.readTree(result.response.contentAsString)["image"]["url"].asText()
    }

    private fun setBanner(
        admin: User,
        game: String,
        seasonId: Long?,
        teamId: Long?,
        picture: String,
    ): ResultActions =
        mvc.perform(
            post("/esports/banners")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        mapOf("game" to game, "seasonId" to seasonId, "teamId" to teamId, "picture" to picture),
                    ),
                )
                .with(bearer(admin)).with(csrfToken()),
        )

    @Nested
    inner class TeamPosters {
        @Test
        fun `an uploaded poster is served publicly and named on the team`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Poster Team")

            val url = posterOn(admin, team, png("poster.png"))
            org.assertj.core.api.Assertions.assertThat(url).startsWith("/files/public/team-posters/")
            org.assertj.core.api.Assertions.assertThat(url.substringAfterLast('/')).contains(".webp")

            // Anonymous: the pages that draw this are public, and so is the image.
            mvc.perform(get(url))
                .andExpect(status().isOk)
                .andExpect(content().contentType("image/webp"))
                // Inline, not an attachment: a browser has to draw this rather than save it.
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
        }

        @Test
        fun `the old row-id public file route is gone`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Old Route Team")
            val url = posterOn(admin, team, png("poster.png"))
            val rowId = fileRepository.findByPath(url.removePrefix("/files/public/")).orElseThrow().id

            mvc.perform(get("/files/public/$rowId"))
                .andExpect(status().isNotFound)
        }

        /**
         * A picture is measured on the way in. The two sides differ so a width reported as a
         * height would fail rather than pass by coincidence, and this one is narrower than
         * every width its kind lists, so it is offered at none of them.
         */
        @Test
        fun `an uploaded poster carries its own size, and a tiny one gets no widths`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Measured Team")

            saveTeam(admin, team, upload(admin, FileType.TEAM_POSTER, png("measured.png", imageOf(6, 4, "png"))))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.poster.width").value(6))
                .andExpect(jsonPath("$.poster.height").value(4))
                .andExpect(jsonPath("$.poster.renditions").isEmpty)
        }

        @Test
        fun `a large uploaded poster is stored as capped WebP`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Large Poster Team")

            saveTeam(admin, team, upload(admin, FileType.TEAM_POSTER, jpeg("large-poster.jpg", 3000, 1200)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.poster.url").value(org.hamcrest.Matchers.endsWith(".webp")))
                .andExpect(jsonPath("$.poster.width").value(2560))
                .andExpect(jsonPath("$.poster.height").value(1024))

            val url = "/files/public/" + teams.findById(team.id!!).orElseThrow().poster!!.path
            val served = mvc.perform(get(url))
                .andExpect(status().isOk)
                .andExpect(content().contentType("image/webp"))
                // Saved from a browser this keeps the name it was uploaded under, but it is
                // WebP now, and offering WebP as a .jpg is a file nothing will open.
                .andExpect(
                    header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("large-poster.webp"),
                    ),
                )
                .andReturn()

            val decoded = decodedWebp(served.response.contentAsByteArray)
            org.assertj.core.api.Assertions.assertThat(decoded.width).isEqualTo(2560)
            org.assertj.core.api.Assertions.assertThat(decoded.height).isEqualTo(1024)
        }

        /**
         * A picture the converter itself refuses.
         *
         * Its header is intact, so its size reads and nothing upstream objects; only the
         * encoder discovers there is nothing behind the header. That is a picture the person
         * uploading it should be told to replace, rather than a fault of the site's — the
         * missing converter that is the site's fault is caught at startup instead.
         */
        @Test
        fun `a picture the converter refuses is refused rather than answered with a server error`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Truncated Poster Team")
            val whole = imageOf(200, 120, "png")

            mvc.perform(
                multipart(PublicFileUrls.UPLOAD)
                    .file(png("truncated.png", whole.copyOf(whole.size / 2)))
                    .param("type", FileType.TEAM_POSTER.name)
                    .with(bearer(admin)).with(csrfToken()),
            ).andExpect(status().isBadRequest)
        }

        @Test
        fun `a narrow poster is not upscaled`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Narrow Poster Team")

            saveTeam(admin, team, upload(admin, FileType.TEAM_POSTER, jpeg("narrow-poster.jpg", 400, 240)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.poster.width").value(400))
                .andExpect(jsonPath("$.poster.height").value(240))

            val url = "/files/public/" + teams.findById(team.id!!).orElseThrow().poster!!.path
            val served = mvc.perform(get(url)).andExpect(status().isOk).andReturn()
            org.assertj.core.api.Assertions.assertThat(decodedWebp(served.response.contentAsByteArray).width).isEqualTo(400)
        }

        @Test
        fun `an over-ceiling WebP poster is resized`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Large WebP Poster Team")
            val webp = webpOf(3000, 1200)

            val picture = upload(admin, FileType.TEAM_POSTER, MockMultipartFile("file", "large-poster.webp", "image/webp", webp))
            saveTeam(admin, team, picture)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.poster.width").value(2560))
                .andExpect(jsonPath("$.poster.height").value(1024))

            val served = mvc.perform(get("/files/public/$picture")).andExpect(status().isOk).andReturn()
            val decoded = decodedWebp(served.response.contentAsByteArray)
            org.assertj.core.api.Assertions.assertThat(decoded.width).isEqualTo(2560)
            org.assertj.core.api.Assertions.assertThat(decoded.height).isEqualTo(1024)
        }

        @Test
        fun `a WebP poster within the ceiling is stored byte-identical`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "WebP Poster Team")
            val webp = webpOf(64, 32)

            val picture = upload(admin, FileType.TEAM_POSTER, MockMultipartFile("file", "poster.webp", "image/webp", webp))
            saveTeam(admin, team, picture)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.poster.width").value(64))
                .andExpect(jsonPath("$.poster.height").value(32))

            val served = mvc.perform(get("/files/public/$picture"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("image/webp"))
                .andReturn()
            org.assertj.core.api.Assertions.assertThat(served.response.contentAsByteArray).isEqualTo(webp)
        }

        /**
         * A mark is a logo, so it is the kind that must not be encoded lossily. Asserted on
         * the served bytes rather than on how the encoder was called: what a viewer gets is
         * the thing the rule is about.
         */
        @Test
        fun `a game mark is encoded losslessly`() {
            val admin = createUserWithRole(Role.ADMIN)
            val source = transparentPng()

            val stored = upload(
                admin,
                FileType.GAME_MARK,
                MockMultipartFile("file", "mark.png", MediaType.IMAGE_PNG_VALUE, source),
            )

            org.assertj.core.api.Assertions.assertThat(stored).startsWith("game-marks/")
            org.assertj.core.api.Assertions.assertThat(stored).endsWith(".webp")

            val served = mvc.perform(get("/files/public/$stored"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("image/webp"))
                .andReturn()

            val original = ImageIO.read(source.inputStream())
            val decoded = decodedWebp(served.response.contentAsByteArray)
            org.assertj.core.api.Assertions.assertThat(decoded.width).isEqualTo(original.width)
            org.assertj.core.api.Assertions.assertThat(decoded.height).isEqualTo(original.height)
            for (x in 0 until original.width) {
                for (y in 0 until original.height) {
                    org.assertj.core.api.Assertions.assertThat(decoded.getRGB(x, y)).isEqualTo(original.getRGB(x, y))
                }
            }
        }

        @Test
        fun `uploading again replaces the poster rather than adding one`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Replacing Team")

            val first = posterOn(admin, team, png("first.png"))
            // Different pixels, or the converted content hash would make the replacement indistinguishable.
            val second = posterOn(admin, team, png("second.png", imageOf(2, 1, "png")))

            org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first)
            mvc.perform(get("/esports/teams?game=VALORANT").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$[?(@.name == 'Replacing Team')].poster.url").value(
                        org.hamcrest.Matchers.contains(second),
                    ),
                )
        }

        /** Naming no picture is what the picker's Remove means once the dialog is saved. */
        @Test
        fun `a save naming no poster leaves the team without one`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Removing Team")
            posterOn(admin, team, png("gone.png"))

            saveTeam(admin, team, null)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.poster").doesNotExist())
        }

        /**
         * A save naming a picture nobody stored is refused rather than quietly dropped: a save
         * that closes the dialog and changes nothing is the one thing that must not happen.
         */
        @Test
        fun `a save naming a picture that is not in storage is refused`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Imaginary Poster Team")

            saveTeam(admin, team, "team-posters/nothing-is-here.webp")
                .andExpect(status().isBadRequest)
        }

        /** A poster field takes a poster, so a directory goes on meaning what it says. */
        @Test
        fun `a save naming a picture of another kind is refused`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team("VALORANT", "Wrong Kind Team")
            val mark = upload(admin, FileType.GAME_MARK, png("mark.png"))

            saveTeam(admin, team, mark).andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class RosterIcons {
        @Test
        fun `an uploaded icon shows on the public page for that entry only`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Icon Season", 2031)
            val team = team("CS2", "Icon Team")
            val withIcon = entry(team, season, "hasicon")
            entry(team, season, "noicon")

            saveEntry(admin, withIcon, upload(admin, FileType.ROSTER_ICON, png("icon.png")))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.icon.url").isNotEmpty)

            mvc.perform(get("/esports/games/CS2?seasonId=${season.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.teams[0].members[?(@.handle == 'hasicon')].icon.url").isNotEmpty)
                .andExpect(jsonPath("$.teams[0].members[?(@.handle == 'noicon')].icon").isEmpty)
        }

        @Test
        fun `a save naming no picture leaves the entry without one`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Icon Removal Season", 2032)
            val team = team("CS2", "Icon Removal Team")
            val entry = entry(team, season, "temporary")

            saveEntry(admin, entry, upload(admin, FileType.ROSTER_ICON, png("icon.png")))
                .andExpect(status().isOk)

            saveEntry(admin, entry, null)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.icon").doesNotExist())
        }
    }

    @Nested
    inner class Banners {
        @Test
        fun `a game's banner carries a page that has nothing narrower`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Banner Season", 2033)
            val team = team("ROCKET_LEAGUE", "Banner Team")
            entry(team, season, "player")

            val url = bannerOn(admin, "ROCKET_LEAGUE", null, null, png("game.png"))

            mvc.perform(get("/esports/games/ROCKET_LEAGUE?seasonId=${season.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.banner.url").value(url))
                .andExpect(jsonPath("$.teams[0].banner.url").value(url))
        }

        @Test
        fun `a team's own banner overrides the game's for that team only`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Override Season", 2034)
            val overridden = team("LEAGUE_OF_LEGENDS", "Override Team")
            val plain = team("LEAGUE_OF_LEGENDS", "Plain Team")
            entry(overridden, season, "one")
            entry(plain, season, "two")

            val gameUrl = bannerOn(admin, "LEAGUE_OF_LEGENDS", null, null, png("g.png"))
            val teamUrl = bannerOn(
                admin, "LEAGUE_OF_LEGENDS", null, overridden.id, png("t.png", imageOf(3, 2, "png")),
            )
            // Two banners of the same picture share one address, and this test would then
            // pass whether or not the narrower one was ever chosen.
            org.assertj.core.api.Assertions.assertThat(teamUrl).isNotEqualTo(gameUrl)

            mvc.perform(get("/esports/games/LEAGUE_OF_LEGENDS?seasonId=${season.id}"))
                .andExpect(status().isOk)
                // The page itself is not narrowed to a team, so it keeps the game's banner.
                .andExpect(jsonPath("$.banner.url").value(gameUrl))
                .andExpect(jsonPath("$.teams[?(@.name == 'Override Team')].banner.url").value(
                    org.hamcrest.Matchers.contains(teamUrl),
                ))
                .andExpect(jsonPath("$.teams[?(@.name == 'Plain Team')].banner.url").value(
                    org.hamcrest.Matchers.contains(gameUrl),
                ))
        }

        @Test
        fun `setting one against a combination that has one replaces it`() {
            val admin = createUserWithRole(Role.ADMIN)
            bannerOn(admin, "GEOGUESSR", null, null, png("first.png"))
            bannerOn(admin, "GEOGUESSR", null, null, png("second.png", imageOf(3, 2, "png")))

            org.assertj.core.api.Assertions.assertThat(banners.findAllByGame("GEOGUESSR")).hasSize(1)
        }

        @Test
        fun `a removed banner falls back to what remains`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Fallback Season", 2035)
            val team = team("TRACKMANIA", "Fallback Team")
            entry(team, season, "driver")

            val gameUrl = bannerOn(admin, "TRACKMANIA", null, null, png("g.png"))
            bannerOn(admin, "TRACKMANIA", season.id, null, png("s.png", imageOf(3, 2, "png")))

            val seasonBanner = banners.findAllByGame("TRACKMANIA").single { it.seasonId == season.id }
            mvc.perform(delete("/esports/banners/${seasonBanner.id}").with(bearer(admin)).with(csrfToken()))
                .andExpect(status().isNoContent)

            mvc.perform(get("/esports/games/TRACKMANIA?seasonId=${season.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.banner.url").value(gameUrl))
        }

        @Test
        fun `a page with no banner anywhere reports none`() {
            val season = season("Bare Season", 2036)
            val team = team("SMASH", "Bare Team")
            entry(team, season, "smasher")

            mvc.perform(get("/esports/games/SMASH?seasonId=${season.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.banner").doesNotExist())
                .andExpect(jsonPath("$.teams[0].banner").doesNotExist())
        }

        @Test
        fun `a banner cannot be narrowed to a team that plays another game`() {
            val admin = createUserWithRole(Role.ADMIN)
            val elsewhere = team("CS2", "Elsewhere Team")

            val picture = upload(admin, FileType.ESPORTS_BANNER, png("wrong.png"))
            setBanner(admin, "VALORANT", null, elsewhere.id, picture)
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class PublicReads {
        /**
         * All three kinds the esports pages draw, fetched by somebody who is not signed in.
         *
         * The payload assertions elsewhere only prove a url was composed. They pass whether or
         * not an anonymous visitor can follow one, which is exactly how a security matcher
         * admitting too few path segments ships green.
         */
        @Test
        fun `a poster, a line-up icon and a banner all load for a visitor who is not signed in`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Anonymous Season", 2037)
            val team = team("VALORANT", "Anonymous Team")
            val member = entry(team, season, "anonymous")

            val poster = posterOn(admin, team, png("poster.png"))
            val iconResult = saveEntry(admin, member, upload(admin, FileType.ROSTER_ICON, png("icon.png", imageOf(4, 4, "png"))))
                .andExpect(status().isOk).andReturn()
            val icon = mapper.readTree(iconResult.response.contentAsString)["icon"]["url"].asText()
            val banner = bannerOn(admin, "VALORANT", null, null, png("banner.png", imageOf(8, 5, "png")))

            for (url in listOf(poster, icon, banner)) {
                mvc.perform(get(url))
                    .andExpect(status().isOk)
                    .andExpect(content().contentType("image/webp"))
            }
        }

        @Test
        fun `a file of a kind that is not public reports missing rather than forbidden`() {
            val document = fileFactory.create(createUserWithRole(Role.ADMIN), type = FileType.DOCUMENT)
            document.path = "documents/private.png"
            persist(document)

            mvc.perform(get("/files/public/${document.path}"))
                .andExpect(status().isNotFound)
        }
    }
}
