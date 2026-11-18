package net.blueshell.api.vote.domain

import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.RotatingSecret
import net.blueshell.api.shared.credentials.WhenCredentialsMissing
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

/**
 * Wires the vote's outside lookups. Games come from IGDB where its credentials are set, and from a
 * stand-in otherwise (api ADR-019): the fixed list where `app.vote.offline-games` allows it, and a
 * catalogue that refuses every search elsewhere, so production never offers made-up games.
 */
@Configuration
class VoteClientConfig {
    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build()

    private fun rest(builder: RestClient.Builder): RestClient =
        builder
            .clone()
            .requestFactory(JdkClientHttpRequestFactory(http).apply { setReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS)) })
            .build()

    @Bean
    @WhenCredentialsSet(Credentials.IGDB_CLIENT_ID, Credentials.IGDB_CLIENT_SECRET)
    fun igdbGameCatalogue(
        builder: RestClient.Builder,
        environment: Environment,
        json: ObjectMapper,
        clock: Clock,
    ): GameCatalogue =
        ThrottledGameCatalogue(
            IgdbGameCatalogue(
                RotatingSecret(environment, Credentials.IGDB_CLIENT_ID),
                RotatingSecret(environment, Credentials.IGDB_CLIENT_SECRET),
                rest(builder),
                json,
                clock,
            ),
            clock,
        )

    @Bean
    @WhenCredentialsMissing(Credentials.IGDB_CLIENT_ID, Credentials.IGDB_CLIENT_SECRET)
    fun standInGameCatalogue(
        @Value($$"${app.vote.offline-games}") offlineGames: Boolean,
    ): GameCatalogue = if (offlineGames) OfflineGameCatalogue() else UnavailableGameCatalogue

    @Bean
    fun trackPreviews(
        builder: RestClient.Builder,
        json: ObjectMapper,
    ): TrackPreviews = OEmbedTrackPreviews(rest(builder), json)

    @Bean
    fun remotePictures(): RemotePictures = HostBoundRemotePictures(http, ::isPictureHost)

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 3L
        const val READ_TIMEOUT_SECONDS = 5L
    }
}
