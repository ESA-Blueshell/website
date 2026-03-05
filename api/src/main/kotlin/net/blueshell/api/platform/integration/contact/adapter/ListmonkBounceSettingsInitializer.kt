package net.blueshell.api.platform.integration.contact.adapter

import net.blueshell.api.platform.config.ListmonkConfig
import net.blueshell.api.platform.config.ListmonkProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * On application startup, ensures Listmonk's bounce processing settings match the desired config.
 *
 * Uses `GET /api/settings` → modify relevant fields → `PUT /api/settings` to avoid
 * resetting unrelated settings. Runs after the application context is fully started
 * so that all beans (including the Listmonk API client) are ready.
 *
 * Tolerant of failures — logs a warning instead of crashing the application.
 */
@Component
@Profile("!test")
class ListmonkBounceSettingsInitializer(
    private val props: ListmonkProperties,
    @Qualifier(ListmonkConfig.ADMIN_REST_CLIENT_BEAN) private val restClient: RestClient,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun configureBounceSettings() {
        try {
            @Suppress("UNCHECKED_CAST")
            val wrapper = restClient.get()
                .uri("/api/settings")
                .retrieve()
                .body(Map::class.java) as? Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val settings = wrapper?.get("data") as? MutableMap<String, Any>
                ?: run {
                    log.warn("Could not read Listmonk settings — skipping bounce configuration")
                    return
                }

            val alreadyEnabled = settings["bounce.enabled"] == true
            val mailboxEnabled = props.bounce.mailbox.enabled

            if (alreadyEnabled && !mailboxEnabled) {
                log.debug("Listmonk bounce processing already enabled; no changes needed")
                return
            }

            // Enable bounce processing
            settings["bounce.enabled"] = true

            if (mailboxEnabled) {
                val mb = props.bounce.mailbox
                if (mb.host.isBlank() || mb.username.isBlank()) {
                    log.warn("LISTMONK_BOUNCE_MAILBOX_ENABLED=true but host/username are not set — skipping mailbox config")
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val mailboxes = (settings["bounce.mailboxes"] as? MutableList<Any>) ?: mutableListOf()
                    if (mailboxes.isEmpty()) {
                        mailboxes.add(buildMailboxConfig(mb))
                        settings["bounce.mailboxes"] = mailboxes
                        log.info("Configuring Listmonk bounce mailbox: host={} port={}", mb.host, mb.port)
                    } else {
                        log.debug("Listmonk already has {} bounce mailbox(es) — not overwriting", mailboxes.size)
                    }
                }
            }

            restClient.put()
                .uri("/api/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(settings)
                .retrieve()
                .toBodilessEntity()

            log.info("Listmonk bounce settings updated (enabled=true, mailbox={})", mailboxEnabled)
        } catch (e: RestClientException) {
            log.warn("Could not configure Listmonk bounce settings (Listmonk may not be reachable yet): {}", e.message)
        } catch (e: Exception) {
            log.warn("Unexpected error configuring Listmonk bounce settings: {}", e.message, e)
        }
    }

    private fun buildMailboxConfig(mb: ListmonkProperties.BounceProperties.MailboxProperties): Map<String, Any> =
        buildMap {
            put("enabled", true)
            put("type", "imap")
            put("host", mb.host)
            put("port", mb.port)
            put("auth_protocol", "userpass")
            put("username", mb.username)
            put("password", mb.password)
            put("tls_enabled", mb.tlsEnabled)
            put("tls_skip_verify", mb.tlsSkipVerify)
            put("folder", mb.folder)
            put("return_path", mb.returnPath)
            put("scan_interval", mb.scanInterval)
        }

    companion object {
        private val log = LoggerFactory.getLogger(ListmonkBounceSettingsInitializer::class.java)
    }
}
