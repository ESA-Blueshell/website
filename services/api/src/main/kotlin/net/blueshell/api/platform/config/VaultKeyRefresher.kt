package net.blueshell.api.platform.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend
import org.springframework.vault.core.VaultTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.concurrent.ConcurrentHashMap

/**
 * Re-reads the Vault KV paths the api imports and puts any key that changed in front
 * of the imported values, then names the changed keys in an [EnvironmentChangeEvent]
 * (api ADR-033). Consumers that built something from a key rebuild it on that event.
 *
 * Not Spring Cloud's context refresh: that re-runs the whole Vault import, which leases a
 * new database login on every poll. KV v2 carries no lease, so polling is the only way a
 * change reaches the api. A read that fails keeps the values in use.
 */
@Component
@ConditionalOnProperty("spring.cloud.vault.enabled", havingValue = "true")
class VaultKeyRefresher(
    private val vault: VaultTemplate,
    private val environment: ConfigurableEnvironment,
    private val events: ApplicationEventPublisher,
) {
    private val latest = ConcurrentHashMap<String, Any>()
    private val paths: List<ImportedPath> = importedPaths(environment)

    // Keys the last read returned, so one Vault drops can be blanked.
    @Volatile private var previous: Set<String> = runCatching { readAll().keys }.getOrDefault(emptySet())

    init {
        environment.propertySources.addFirst(MapPropertySource(SOURCE_NAME, latest))
    }

    @Scheduled(
        fixedDelayString = "\${app.vault.refresh-interval}",
        initialDelayString = "\${app.vault.refresh-interval}",
    )
    fun refresh() {
        val read =
            try {
                readAll()
            } catch (e: RuntimeException) {
                log.warn("Vault could not be read; keeping the secrets in use: {}", e.message)
                return
            }
        val changed = changedKeys(read)
        previous = read.keys
        if (changed.isEmpty()) return

        changed.forEach { key -> latest[key] = read[key] ?: "" }
        log.info("Vault keys changed: {}", changed.sorted())
        events.publishEvent(EnvironmentChangeEvent(changed))
    }

    // A key Vault no longer holds is blank, as it would be on a fresh start.
    private fun changedKeys(read: Map<String, String>): Set<String> =
        (read.keys + previous).filterTo(mutableSetOf()) { key ->
            environment.getProperty(key) != (read[key] ?: "")
        }

    private fun readAll(): Map<String, String> = paths.flatMap(::read).toMap()

    private fun read(path: ImportedPath): List<Pair<String, String>> {
        val data =
            vault
                .opsForKeyValue(path.mount, KeyValueBackend.KV_2)
                .get(path.key)
                ?.data
                .orEmpty()
        return data.map { (key, value) -> path.prefix + key to value.toString() }
    }

    /** A `vault://mount/key?prefix=...` location from spring.config.import. */
    private data class ImportedPath(
        val mount: String,
        val key: String,
        val prefix: String,
    )

    private companion object {
        private val log = LoggerFactory.getLogger(VaultKeyRefresher::class.java)
        const val SOURCE_NAME = "vault-refreshed"
        private const val SCHEME = "vault://"

        // The bare `vault://` import is the secret backends, which lease rather than poll.
        fun importedPaths(environment: ConfigurableEnvironment): List<ImportedPath> =
            Binder
                .get(environment)
                .bind("spring.config.import", Bindable.listOf(String::class.java))
                .orElse(emptyList())
                .orEmpty()
                .map { it.removePrefix("optional:") }
                .filter { it.startsWith(SCHEME) && it.length > SCHEME.length }
                .map { location ->
                    val uri = UriComponentsBuilder.fromUriString(location.removePrefix(SCHEME)).build()
                    val path = uri.path.orEmpty().trim('/')
                    ImportedPath(
                        mount = path.substringBefore('/'),
                        key = path.substringAfter('/'),
                        prefix = uri.queryParams.getFirst("prefix").orEmpty(),
                    )
                }
    }
}
