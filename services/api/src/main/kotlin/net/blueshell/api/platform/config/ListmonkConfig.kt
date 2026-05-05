package net.blueshell.api.platform.config

import net.blueshell.clients.listmonk.ApiClient
import net.blueshell.clients.listmonk.api.BouncesApi
import net.blueshell.clients.listmonk.api.ListsApi
import net.blueshell.clients.listmonk.api.SubscribersApi
import net.blueshell.clients.listmonk.api.TransactionalApi
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import java.io.File
import java.util.Base64

/**
 * Wires HTTP clients for the api → Listmonk integration.
 *
 * Scope is intentionally narrow: this api only *reads from* and *sends to*
 * Listmonk. It does not configure Listmonk. Templates, SMTP settings, bounce
 * processing settings, and the API user are managed by
 * `platform/cluster/flux/apps/stateless/listmonk/setup.py` (the setup Job)
 * or the Listmonk admin UI.
 *
 * Beans exposed:
 *  - [TransactionalApi]: `POST /api/tx` for sending transactional emails.
 *  - [BouncesApi]: `GET /api/bounces` for the read-only bounce poller.
 *  - [SubscribersApi] / [ListsApi]: contact + list sync (subscribers are user data).
 */
@Configuration
@EnableConfigurationProperties(ListmonkProperties::class)
class ListmonkConfig {

    @Bean
    @Profile("!test")
    fun listmonkApiClient(props: ListmonkProperties): ApiClient {
        val (username, password) = readApiTokenFile(props)?.also { (apiUser, _) ->
            log.info("Listmonk: using API token auth (user={})", apiUser)
        } ?: run {
            log.info("Listmonk: using basic auth (user={})", props.api.username)
            props.api.username to props.api.password
        }
        val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        val restClient = ApiClient.buildRestClientBuilder()
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $encoded")
            .build()
        val client = ApiClient(restClient)
        client.basePath = props.api.baseUrl
        return client
    }

    @Bean
    @Profile("!test")
    fun listmonkTransactionalApi(client: ApiClient): TransactionalApi = TransactionalApi(client)

    @Bean
    @Profile("!test")
    fun listmonkBouncesApi(client: ApiClient): BouncesApi = BouncesApi(client)

    @Bean
    @Profile("!test")
    fun listmonkSubscribersApi(client: ApiClient): SubscribersApi = SubscribersApi(client)

    @Bean
    @Profile("!test")
    fun listmonkListsApi(client: ApiClient): ListsApi = ListsApi(client)

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkConfig::class.java)

        /**
         * Reads the API token written by Listmonk's `--install` step when
         * `LISTMONK_ADMIN_API_USER` is set.
         *
         * The file contains a line like: `LISTMONK_ADMIN_API_TOKEN=<token>`
         *
         * @return (apiUser, token) pair, or null if the file is absent / empty.
         */
        fun readApiTokenFile(props: ListmonkProperties): Pair<String, String>? {
            return try {
                val file = File(props.api.tokenFile)
                if (!file.exists() || file.length() == 0L) return null

                val tokenLine = file.readLines()
                    .firstOrNull { it.startsWith("LISTMONK_ADMIN_API_TOKEN=") }
                    ?: return null

                val token = tokenLine
                    .removePrefix("LISTMONK_ADMIN_API_TOKEN=")
                    .trim()
                    .trim('"')

                if (token.isEmpty()) null
                else Pair(props.api.apiUser, token)
            } catch (e: Exception) {
                log.warn("Could not read Listmonk API token file '{}': {}", props.api.tokenFile, e.message)
                null
            }
        }
    }
}
