package net.blueshell.api.vote.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

class VoteLookupConfigTest {
    private val config = VoteLookupConfig()
    private val json = JsonMapper.builder().build()
    private val clock = Clock.systemUTC()

    private fun environment(vararg profiles: String) = MockEnvironment().apply { setActiveProfiles(*profiles) }

    @Test
    fun `IGDB is used once both credentials are set`() {
        assertThat(config.gameCatalogue("id", "secret", json, clock, environment("prod"))).isInstanceOf(IgdbGameCatalogue::class.java)
    }

    @Test
    fun `without both credentials, development and tests get the fixed list`() {
        assertThat(config.gameCatalogue("id", "", json, clock, environment("dev"))).isInstanceOf(OfflineGameCatalogue::class.java)
        assertThat(config.gameCatalogue("", "secret", json, clock, environment("test"))).isInstanceOf(OfflineGameCatalogue::class.java)
    }

    @Test
    fun `without credentials anywhere else, every search is refused`() {
        val catalogue = config.gameCatalogue("", "", json, clock, environment("prod"))

        assertThatThrownBy { catalogue.search("celeste", 5, null) }.isInstanceOf(GameSearchUnavailable::class.java)
        assertThatThrownBy { catalogue.byId(1) }.isInstanceOf(GameSearchUnavailable::class.java)
    }

    @Test
    fun `track previews come from oEmbed, and pictures from the known hosts`() {
        assertThat(config.trackPreviews(json)).isInstanceOf(OEmbedTrackPreviews::class.java)
        assertThat(config.remotePictures()).isInstanceOf(HostBoundRemotePictures::class.java)
    }

    @Test
    fun `the fixed list finds by name, by year and by id`() {
        val offline = OfflineGameCatalogue()

        assertThat(offline.search("hollow", 5, null).map { it.name }).containsExactly("Hollow Knight", "Hollow Knight: Silksong")
        assertThat(offline.search("hollow", 5, releasedIn = 2025).map { it.name }).containsExactly("Hollow Knight: Silksong")
        assertThat(offline.search("e", 2, null)).hasSize(2)
        assertThat(offline.byId(offline.search("Celeste", 1, null).single().igdbId)?.name).isEqualTo("Celeste")
        assertThat(offline.byId(-1)).isNull()
    }
}
