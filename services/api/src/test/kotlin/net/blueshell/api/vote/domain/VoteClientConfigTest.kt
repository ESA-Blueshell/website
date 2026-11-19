package net.blueshell.api.vote.domain

import com.sun.net.httpserver.HttpServer
import net.blueshell.api.shared.credentials.Credentials
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CountDownLatch

class VoteClientConfigTest {
    private val runner =
        ApplicationContextRunner()
            .withBean(RestClient.Builder::class.java, { RestClient.builder() })
            .withBean(ObjectMapper::class.java, { JsonMapper.builder().build() })
            .withBean(Clock::class.java, { Clock.systemUTC() })
            .withUserConfiguration(VoteClientConfig::class.java)

    private fun catalogueWith(vararg properties: String): GameCatalogue {
        var found: GameCatalogue? = null
        runner.withPropertyValues(*properties).run { context ->
            assertThat(context).hasSingleBean(GameCatalogue::class.java)
            found = context.getBean(GameCatalogue::class.java)
        }
        return found!!
    }

    @Test
    fun `with both IGDB credentials, games come from IGDB under the throttle`() {
        val catalogue =
            catalogueWith(
                "${Credentials.IGDB_CLIENT_ID}=id",
                "${Credentials.IGDB_CLIENT_SECRET}=secret",
                "app.vote.offline-games=true",
            )

        assertThat(catalogue).isInstanceOf(ThrottledGameCatalogue::class.java)
    }

    @Test
    fun `without both, the stand-in offers the fixed list only where offline games are allowed`() {
        val halfSet = catalogueWith("${Credentials.IGDB_CLIENT_ID}=id", "app.vote.offline-games=true")
        assertThat(halfSet).isInstanceOf(OfflineGameCatalogue::class.java)

        val refusing = catalogueWith("${Credentials.IGDB_CLIENT_ID}=", "${Credentials.IGDB_CLIENT_SECRET}=")
        assertThatThrownBy { refusing.search("celeste", 5, null) }.isInstanceOf(GameSearchUnavailable::class.java)
        assertThatThrownBy { refusing.byId(1) }.isInstanceOf(GameSearchUnavailable::class.java)
        assertThat(refusing.coverUrl("co1", CoverSize.SMALL)).isNull()
    }

    @Test
    fun `track previews come from oEmbed, and pictures from the known hosts`() {
        runner.run { context ->
            assertThat(context.getBean(TrackPreviews::class.java)).isInstanceOf(OEmbedTrackPreviews::class.java)
            assertThat(context.getBean(RemotePictures::class.java)).isInstanceOf(HostBoundRemotePictures::class.java)
        }
    }

    @Test
    fun `the vote's client gives up on an answer that does not come`() {
        val silent = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val release = CountDownLatch(1)
        silent.createContext("/") { release.await() }
        silent.start()
        try {
            val rest = VoteClientConfig(readTimeout = Duration.ofMillis(200)).voteRestClient(RestClient.builder())
            val started = System.nanoTime()

            assertThatThrownBy {
                rest
                    .get()
                    .uri("http://127.0.0.1:${silent.address.port}/")
                    .retrieve()
                    .body(String::class.java)
            }.isInstanceOf(ResourceAccessException::class.java)
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3))
        } finally {
            release.countDown()
            silent.stop(0)
        }
    }

    @Test
    fun `the fixed list finds by name, by year and by id`() {
        val offline = OfflineGameCatalogue()

        assertThat(offline.search("hollow", 5, null).map { it.name }).containsExactly("Hollow Knight", "Hollow Knight: Silksong")
        assertThat(offline.search("hollow", 5, releasedIn = 2025).map { it.name }).containsExactly("Hollow Knight: Silksong")
        assertThat(offline.search("e", 2, null)).hasSize(2)
        assertThat(offline.byId(offline.search("Celeste", 1, null).single().igdbId)?.name).isEqualTo("Celeste")
        assertThat(offline.byId(-1)).isNull()
        assertThat(offline.coverUrl("co1", CoverSize.SMALL)).isNull()
    }
}
