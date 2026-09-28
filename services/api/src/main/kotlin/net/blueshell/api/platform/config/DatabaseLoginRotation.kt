package net.blueshell.api.platform.config

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.lease.SecretLeaseContainer
import org.springframework.vault.core.lease.domain.RequestedSecret
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent
import org.springframework.vault.core.lease.event.SecretLeaseEvent
import javax.sql.DataSource

/**
 * Keeps the connection pool on a login Vault still honours (api ADR-033).
 *
 * Spring Cloud Vault leases the login the pool starts with and renews it only up to the
 * role's `max_ttl`, after which Vault drops the user. This asks for the same role as a
 * rotating secret, so the container requests a fresh login before each lease runs out,
 * and hands every new one to Hikari. New connections use it; the ones on the old login
 * are retired as they come back to the pool.
 */
@Component
@ConditionalOnProperty("spring.cloud.vault.database.enabled", havingValue = "true")
class DatabaseLoginRotation(
    container: SecretLeaseContainer,
    dataSource: DataSource,
    @Value("\${spring.cloud.vault.database.backend:database}") backend: String,
    @Value("\${spring.cloud.vault.database.role}") role: String,
) {
    private val pool: HikariDataSource = dataSource.unwrap(HikariDataSource::class.java)
    private val path = "$backend/creds/$role"

    init {
        container.addLeaseListener(::onLease)
        container.addRequestedSecret(RequestedSecret.rotating(path))
    }

    private fun onLease(event: SecretLeaseEvent) {
        if (event !is SecretLeaseCreatedEvent || event.source.path != path) return
        val username = event.secrets["username"] as? String ?: return
        val password = event.secrets["password"] as? String ?: return
        pool.hikariConfigMXBean.setUsername(username)
        pool.hikariConfigMXBean.setPassword(password)
        pool.hikariPoolMXBean?.softEvictConnections()
        log.info("Database login rotated to {}", username)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(DatabaseLoginRotation::class.java)
    }
}
