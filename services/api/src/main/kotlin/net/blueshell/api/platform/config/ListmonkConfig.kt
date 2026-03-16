package net.blueshell.api.platform.config

import net.blueshell.clients.listmonk.api.BouncesApi
import net.blueshell.clients.listmonk.api.ListsApi
import net.blueshell.clients.listmonk.api.SubscribersApi
import net.blueshell.clients.listmonk.api.TemplatesApi
import net.blueshell.clients.listmonk.api.TransactionalApi
import net.blueshell.clients.listmonk.ApiClient
import net.blueshell.clients.listmonk.model.NewTemplate
import net.blueshell.clients.listmonk.model.NewTemplateType
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import java.io.File
import java.util.Base64

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

    /**
     * Pre-configured [RestClient] for Listmonk admin API calls (e.g. settings).
     * Uses the API token from the secrets volume when available; falls back to basic auth.
     */
    @Bean(ADMIN_REST_CLIENT_BEAN)
    @Profile("!test")
    fun listmonkAdminRestClient(props: ListmonkProperties): RestClient {
        val baseUrl = props.api.baseUrl.removeSuffix("/api")
        val (username, password) = readApiTokenFile(props) ?: (props.api.username to props.api.password)
        val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $encoded")
            .build()
    }

    @Bean
    @Profile("!test")
    fun listmonkTemplatesApi(client: ApiClient): TemplatesApi = TemplatesApi(client)

    @Bean
    @Profile("!test")
    fun listmonkSubscribersApi(client: ApiClient): SubscribersApi = SubscribersApi(client)

    @Bean
    @Profile("!test")
    fun listmonkListsApi(client: ApiClient): ListsApi = ListsApi(client)

    /**
     * Resolves the Listmonk transactional template ID to use for sending emails.
     *
     * If [ListmonkProperties.templateId] is explicitly configured (> 0), it is used as-is.
     * Otherwise, the template named [TEMPLATE_NAME] is looked up via the Listmonk API and
     * created if it does not exist yet. This means no manual setup is needed in the Listmonk UI.
     *
     * The template renders the full pre-built HTML passed via `data.body` in each transactional send.
     */
    @Bean(TEMPLATE_ID_BEAN)
    @Profile("!test")
    fun resolvedListmonkTemplateId(
        templatesApi: TemplatesApi,
        props: ListmonkProperties,
    ): Int {
        if (props.templateId > 0) {
            log.info("Using configured Listmonk template id={}", props.templateId)
            return props.templateId
        }

        val templates = try {
            templatesApi.getTemplates(true)?.data ?: emptyList()
        } catch (e: Exception) {
            log.warn("Could not list Listmonk templates: {}", e.message)
            emptyList()
        }

        val existing = templates.find { it.name == TEMPLATE_NAME && it.type == "tx" }
        if (existing?.id != null) {
            log.info("Found Listmonk template '{}' id={}", TEMPLATE_NAME, existing.id)
            return existing.id!!
        }

        val created = try {
            val req = NewTemplate()
                .name(TEMPLATE_NAME)
                .type(NewTemplateType.TX)
                .subject("{{ .Tx.Subject }}")  // forwarded from each transactional send request
                .body(TEMPLATE_BODY)
            templatesApi.createTemplate(req)?.data
        } catch (e: Exception) {
            throw IllegalStateException("Failed to create Listmonk template '$TEMPLATE_NAME': ${e.message}", e)
        } ?: throw IllegalStateException("Listmonk returned null when creating template '$TEMPLATE_NAME'")

        log.info("Created Listmonk template '{}' id={}", TEMPLATE_NAME, created.id)
        return created.id!!
    }

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkConfig::class.java)

        const val TEMPLATE_ID_BEAN = "resolvedListmonkTemplateId"
        const val ADMIN_REST_CLIENT_BEAN = "listmonkAdminRestClient"

        /** Name used to look up or create the passthrough transactional template. */
        const val TEMPLATE_NAME = "Blueshell Transactional"

        /**
         * Minimal passthrough template: renders the full HTML passed in `data.body`.
         * Listmonk wraps the body in its own MIME envelope, so this is all we need.
         */
        const val TEMPLATE_BODY = "{{ .Tx.Data.body }}"

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
