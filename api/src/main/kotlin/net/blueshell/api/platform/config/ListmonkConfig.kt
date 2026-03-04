package net.blueshell.api.platform.config

import net.blueshell.clients.listmonk.api.BouncesApi
import net.blueshell.clients.listmonk.api.TemplatesApi
import net.blueshell.clients.listmonk.api.TransactionalApi
import net.blueshell.clients.listmonk.invoker.ApiClient
import net.blueshell.clients.listmonk.model.NewTemplate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableConfigurationProperties(ListmonkProperties::class)
class ListmonkConfig {

    @Bean
    @Profile("!test")
    fun listmonkApiClient(props: ListmonkProperties): ApiClient {
        val client = ApiClient()
        client.basePath = props.api.baseUrl
        client.setUsername(props.api.username)
        client.setPassword(props.api.password)
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
    fun listmonkTemplatesApi(client: ApiClient): TemplatesApi = TemplatesApi(client)

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
                .type("tx")
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

        /** Name used to look up or create the passthrough transactional template. */
        const val TEMPLATE_NAME = "Blueshell Transactional"

        /**
         * Minimal passthrough template: renders the full HTML passed in `data.body`.
         * Listmonk wraps the body in its own MIME envelope, so this is all we need.
         */
        const val TEMPLATE_BODY = "{{ .Tx.Data.body }}"
    }
}
