package net.blueshell.api.vote.domain

import net.blueshell.api.shared.credentials.Credentials
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

class VoteClientConfigTest {
    private val config = VoteClientConfig()
    private val json = JsonMapper.builder().build()
    private val clock = Clock.systemUTC()

    private val builder = RestClient.builder()

    @Test
    fun `with IGDB credentials, games come from IGDB under the throttle`() {
        val environment =
            MockEnvironment()
                .withProperty(Credentials.IGDB_CLIENT_ID, "id")
                .withProperty(Credentials.IGDB_CLIENT_SECRET, "secret")

        assertThat(config.igdbGameCatalogue(builder, environment, json, clock)).isInstanceOf(ThrottledGameCatalogue::class.java)
    }

    @Test
    fun `without them, the stand-in offers the fixed list only where offline games are allowed`() {
        assertThat(config.standInGameCatalogue(offlineGames = true)).isInstanceOf(OfflineGameCatalogue::class.java)

        val refusing = config.standInGameCatalogue(offlineGames = false)
        assertThatThrownBy { refusing.search("celeste", 5, null) }.isInstanceOf(GameSearchUnavailable::class.java)
        assertThatThrownBy { refusing.byId(1) }.isInstanceOf(GameSearchUnavailable::class.java)
    }

    @Test
    fun `track previews come from oEmbed, and pictures from the known hosts`() {
        assertThat(config.trackPreviews(builder, json)).isInstanceOf(OEmbedTrackPreviews::class.java)
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
