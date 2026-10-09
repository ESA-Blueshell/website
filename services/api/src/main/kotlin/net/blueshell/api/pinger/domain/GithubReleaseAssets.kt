package net.blueshell.api.pinger.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import net.blueshell.api.pinger.api.ReleaseAsset
import net.blueshell.api.pinger.api.ReleaseAssets
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Reads the project's latest release from the GitHub releases API, so the download endpoint can
 * hand a member the installer that release attached.
 *
 * The call is anonymous: the assets are public, so no token is sent. A release that cannot be read
 * answers as no assets, which the endpoint turns into a 404 rather than an error.
 */
@Service
class GithubReleaseAssets(
    restClientBuilder: RestClient.Builder,
    @param:Value($$"${pinger.app.releases-url:https://api.github.com/repos/ESA-Blueshell/website/releases/latest}")
    private val releasesUrl: String,
) : ReleaseAssets {
    private val client = restClientBuilder.build()

    override fun latest(): List<ReleaseAsset> =
        try {
            client
                .get()
                .uri(releasesUrl)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(GithubRelease::class.java)
                ?.assets
                ?.map { ReleaseAsset(name = it.name, downloadUrl = it.downloadUrl) }
                ?: emptyList()
        } catch (e: RestClientException) {
            log.warn("Could not read the latest release from {}", releasesUrl, e)
            emptyList()
        }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GithubRelease(
        val assets: List<GithubAsset> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GithubAsset(
        val name: String,
        @param:JsonProperty("browser_download_url") val downloadUrl: String,
    )

    private companion object {
        private val log = LoggerFactory.getLogger(GithubReleaseAssets::class.java)
    }
}
