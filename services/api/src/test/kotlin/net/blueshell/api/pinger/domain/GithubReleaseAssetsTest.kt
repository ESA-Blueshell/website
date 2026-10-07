package net.blueshell.api.pinger.domain

import net.blueshell.api.pinger.api.ReleaseAsset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

private const val RELEASES_URL = "https://github.test/repos/ESA-Blueshell/website/releases/latest"

class GithubReleaseAssetsTest {
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val assets = GithubReleaseAssets(builder, RELEASES_URL)

    @Test
    fun `it maps the latest release's assets to name and download url`() {
        server
            .expect(requestTo(RELEASES_URL))
            .andRespond(
                withSuccess(
                    """
                    {
                      "tag_name": "v1.2.3",
                      "assets": [
                        {"name": "Blueshell-Pinger.dmg", "browser_download_url": "https://host/app.dmg", "size": 10},
                        {"name": "Blueshell-Pinger.exe", "browser_download_url": "https://host/app.exe", "size": 20}
                      ]
                    }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThat(assets.latest())
            .containsExactly(
                ReleaseAsset("Blueshell-Pinger.dmg", "https://host/app.dmg"),
                ReleaseAsset("Blueshell-Pinger.exe", "https://host/app.exe"),
            )
        server.verify()
    }

    @Test
    fun `a release with no assets reads as an empty list`() {
        server
            .expect(requestTo(RELEASES_URL))
            .andRespond(withSuccess("""{"tag_name": "v1.2.3"}""", MediaType.APPLICATION_JSON))

        assertThat(assets.latest()).isEmpty()
        server.verify()
    }

    @Test
    fun `a release that cannot be read answers as no assets`() {
        server
            .expect(requestTo(RELEASES_URL))
            .andRespond(withServerError())

        assertThat(assets.latest()).isEmpty()
        server.verify()
    }
}
