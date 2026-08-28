package net.blueshell.api.esports.web

import net.blueshell.api.esports.domain.TeamSeasonService
import net.blueshell.api.esports.persistence.EsportsBannerRepository
import net.blueshell.api.esports.persistence.Season
import net.blueshell.api.esports.persistence.SeasonRepository
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TeamRole
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

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

    /** A one-pixel PNG: the smallest thing that is genuinely the content type it claims. */
    private val pngBytes = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )

    private fun png(name: String, bytes: ByteArray = pngBytes) =
        MockMultipartFile("file", name, MediaType.IMAGE_PNG_VALUE, bytes)

    private fun season(name: String, year: Int): Season = seasons.save(
        Season(
            name = name,
            startDate = LocalDate.of(year, 9, 1),
            endDate = LocalDate.of(year + 1, 6, 30),
        ),
    )

    private fun team(game: Game, name: String): Team = teams.save(Team(game = game, name = name))

    private fun entry(team: Team, season: Season, handle: String): TeamRosterEntry {
        fielded.field(team.id!!, season.id!!)
        return entries.save(
            TeamRosterEntry(team = team, season = season, handle = handle, teamRole = TeamRole.PLAYER),
        )
    }

    @Nested
    inner class TeamPosters {
        @Test
        fun `an uploaded poster is served publicly and named on the team`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team(Game.VALORANT, "Poster Team")

            val posted = mvc.perform(
                multipart("/esports/teams/${team.id}/poster").file(png("poster.png"))
                    .with(bearer(admin)).with(csrfToken()),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.posterUrl").isNotEmpty)
                .andReturn()

            val url = mapper.readTree(posted.response.contentAsString)["posterUrl"].asText()

            // Anonymous: the pages that draw this are public, and so is the image.
            mvc.perform(get(url))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                // Inline, not an attachment: a browser has to draw this rather than save it.
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
        }

        @Test
        fun `uploading again replaces the poster rather than adding one`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team(Game.VALORANT, "Replacing Team")

            val first = uploadPoster(admin, team, png("first.png"))
            // Different bytes, or the content hash would make the replacement indistinguishable.
            val second = uploadPoster(admin, team, png("second.png", pngBytes + byteArrayOf(0)))

            org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first)
            mvc.perform(get("/esports/teams?game=VALORANT").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$[?(@.name == 'Replacing Team')].posterUrl").value(
                        org.hamcrest.Matchers.contains(second),
                    ),
                )
        }

        @Test
        fun `a removed poster leaves the team without one`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team(Game.VALORANT, "Removing Team")
            uploadPoster(admin, team, png("gone.png"))

            mvc.perform(delete("/esports/teams/${team.id}/poster").with(bearer(admin)).with(csrfToken()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.posterUrl").doesNotExist())
        }

        @Test
        fun `a visitor may not upload a poster`() {
            val team = team(Game.VALORANT, "Guarded Team")
            mvc.perform(multipart("/esports/teams/${team.id}/poster").file(png("no.png")).with(csrfToken()))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun `a poster has to be an image`() {
            val admin = createUserWithRole(Role.ADMIN)
            val team = team(Game.VALORANT, "Typed Team")
            val pdf = MockMultipartFile("file", "poster.pdf", MediaType.APPLICATION_PDF_VALUE, pngBytes)

            mvc.perform(
                multipart("/esports/teams/${team.id}/poster").file(pdf)
                    .with(bearer(admin)).with(csrfToken()),
            ).andExpect(status().isUnsupportedMediaType)
        }

        private fun uploadPoster(admin: net.blueshell.api.user.persistence.User, team: Team, file: MockMultipartFile): String {
            val result = mvc.perform(
                multipart("/esports/teams/${team.id}/poster").file(file)
                    .with(bearer(admin)).with(csrfToken()),
            ).andExpect(status().isOk).andReturn()
            return mapper.readTree(result.response.contentAsString)["posterUrl"].asText()
        }
    }

    @Nested
    inner class RosterIcons {
        @Test
        fun `an uploaded icon shows on the public page for that entry only`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Icon Season", 2031)
            val team = team(Game.CS2, "Icon Team")
            val withIcon = entry(team, season, "hasicon")
            entry(team, season, "noicon")

            mvc.perform(
                multipart("/esports/roster/${withIcon.id}/icon").file(png("icon.png"))
                    .with(bearer(admin)).with(csrfToken()),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.iconUrl").isNotEmpty)

            mvc.perform(get("/esports/games/CS2?seasonId=${season.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.teams[0].members[?(@.handle == 'hasicon')].iconUrl").isNotEmpty)
                .andExpect(jsonPath("$.teams[0].members[?(@.handle == 'noicon')].iconUrl").isEmpty)
        }

        @Test
        fun `a removed icon leaves the entry without one`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Icon Removal Season", 2032)
            val team = team(Game.CS2, "Icon Removal Team")
            val entry = entry(team, season, "temporary")

            mvc.perform(
                multipart("/esports/roster/${entry.id}/icon").file(png("icon.png"))
                    .with(bearer(admin)).with(csrfToken()),
            ).andExpect(status().isOk)

            mvc.perform(delete("/esports/roster/${entry.id}/icon").with(bearer(admin)).with(csrfToken()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.iconUrl").doesNotExist())
        }
    }

    @Nested
    inner class Banners {
        @Test
        fun `a game's banner carries a page that has nothing narrower`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Banner Season", 2033)
            val team = team(Game.ROCKET_LEAGUE, "Banner Team")
            entry(team, season, "player")

            val url = uploadBanner(admin, Game.ROCKET_LEAGUE, null, null, png("game.png"))

            mvc.perform(get("/esports/games/ROCKET_LEAGUE?seasonId=${season.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.bannerUrl").value(url))
                .andExpect(jsonPath("$.teams[0].bannerUrl").value(url))
        }

        @Test
        fun `a team's own banner overrides the game's for that team only`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Override Season", 2034)
            val overridden = team(Game.LEAGUE_OF_LEGENDS, "Override Team")
            val plain = team(Game.LEAGUE_OF_LEGENDS, "Plain Team")
            entry(overridden, season, "one")
            entry(plain, season, "two")

            val gameUrl = uploadBanner(admin, Game.LEAGUE_OF_LEGENDS, null, null, png("g.png"))
            val teamUrl = uploadBanner(
                admin, Game.LEAGUE_OF_LEGENDS, null, overridden.id, png("t.png", pngBytes + byteArrayOf(1)),
            )

            mvc.perform(get("/esports/games/LEAGUE_OF_LEGENDS?seasonId=${season.id}"))
                .andExpect(status().isOk)
                // The page itself is not narrowed to a team, so it keeps the game's banner.
                .andExpect(jsonPath("$.bannerUrl").value(gameUrl))
                .andExpect(jsonPath("$.teams[?(@.name == 'Override Team')].bannerUrl").value(
                    org.hamcrest.Matchers.contains(teamUrl),
                ))
                .andExpect(jsonPath("$.teams[?(@.name == 'Plain Team')].bannerUrl").value(
                    org.hamcrest.Matchers.contains(gameUrl),
                ))
        }

        @Test
        fun `uploading against a combination that has one replaces it`() {
            val admin = createUserWithRole(Role.ADMIN)
            uploadBanner(admin, Game.GEOGUESSR, null, null, png("first.png"))
            uploadBanner(admin, Game.GEOGUESSR, null, null, png("second.png", pngBytes + byteArrayOf(2)))

            org.assertj.core.api.Assertions.assertThat(banners.findAllByGame(Game.GEOGUESSR)).hasSize(1)
        }

        @Test
        fun `a removed banner falls back to what remains`() {
            val admin = createUserWithRole(Role.ADMIN)
            val season = season("Fallback Season", 2035)
            val team = team(Game.TRACKMANIA, "Fallback Team")
            entry(team, season, "driver")

            val gameUrl = uploadBanner(admin, Game.TRACKMANIA, null, null, png("g.png"))
            uploadBanner(admin, Game.TRACKMANIA, season.id, null, png("s.png", pngBytes + byteArrayOf(3)))

            val seasonBanner = banners.findAllByGame(Game.TRACKMANIA).single { it.seasonId == season.id }
            mvc.perform(delete("/esports/banners/${seasonBanner.id}").with(bearer(admin)).with(csrfToken()))
                .andExpect(status().isNoContent)

            mvc.perform(get("/esports/games/TRACKMANIA?seasonId=${season.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.bannerUrl").value(gameUrl))
        }

        @Test
        fun `a page with no banner anywhere reports none`() {
            val season = season("Bare Season", 2036)
            val team = team(Game.SMASH, "Bare Team")
            entry(team, season, "smasher")

            mvc.perform(get("/esports/games/SMASH?seasonId=${season.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.bannerUrl").doesNotExist())
                .andExpect(jsonPath("$.teams[0].bannerUrl").doesNotExist())
        }

        @Test
        fun `a banner cannot be narrowed to a team that plays another game`() {
            val admin = createUserWithRole(Role.ADMIN)
            val elsewhere = team(Game.CS2, "Elsewhere Team")

            mvc.perform(
                multipart("/esports/banners").file(png("wrong.png"))
                    .param("game", Game.VALORANT.name)
                    .param("teamId", elsewhere.id.toString())
                    .with(bearer(admin)).with(csrfToken()),
            ).andExpect(status().isBadRequest)
        }

        private fun uploadBanner(
            admin: net.blueshell.api.user.persistence.User,
            game: Game,
            seasonId: Long?,
            teamId: Long?,
            file: MockMultipartFile,
        ): String {
            val request = multipart("/esports/banners").file(file).param("game", game.name)
            seasonId?.let { request.param("seasonId", it.toString()) }
            teamId?.let { request.param("teamId", it.toString()) }
            val result = mvc.perform(request.with(bearer(admin)).with(csrfToken()))
                .andExpect(status().isOk).andReturn()
            return mapper.readTree(result.response.contentAsString)["url"].asText()
        }
    }

    @Nested
    inner class PublicReads {
        @Test
        fun `a file of a kind that is not public reports missing rather than forbidden`() {
            val document = fileFactory.create(createUserWithRole(Role.ADMIN), type = FileType.EVENT_BANNER)

            mvc.perform(get("/files/public/${document.id}"))
                .andExpect(status().isNotFound)
        }
    }
}
