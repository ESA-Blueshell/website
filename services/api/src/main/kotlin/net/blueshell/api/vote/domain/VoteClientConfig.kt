package net.blueshell.api.vote.domain

import net.blueshell.api.file.api.HostBoundRemotePictures
import net.blueshell.api.file.api.RemotePictures
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

/**
 * Picks where games are looked up: IGDB when its credentials are set; otherwise a fixed list in
 * development and tests, and nothing anywhere else, so production without credentials refuses a
 * search rather than offering a made-up catalogue.
 */
@Configuration
class VoteClientConfig {
    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build()

    @Bean
    fun gameCatalogue(
        @Value($$"${app.vote.igdb.client-id:}") clientId: String,
        @Value($$"${app.vote.igdb.client-secret:}") clientSecret: String,
        json: ObjectMapper,
        clock: Clock,
        environment: Environment,
    ): GameCatalogue =
        when {
            clientId.isNotBlank() && clientSecret.isNotBlank() ->
                ThrottledGameCatalogue(IgdbGameCatalogue(clientId, clientSecret, http, json, clock), clock)
            environment.acceptsProfiles(Profiles.of("dev", "test")) -> OfflineGameCatalogue()
            else -> UnavailableGameCatalogue
        }

    @Bean
    fun trackPreviews(json: ObjectMapper): TrackPreviews = OEmbedTrackPreviews(http, json)

    @Bean
    fun remotePictures(): RemotePictures = HostBoundRemotePictures(http, ::pictureHost)

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 3L
    }
}
