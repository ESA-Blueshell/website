package net.blueshell.api.email.domain

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultTemplate
import java.util.concurrent.ConcurrentHashMap

/** An SMTP username and password. Never kept in the database, never answered back. */
data class SmtpLogin(
    val username: String,
    val password: String,
) {
    override fun toString() = "SmtpLogin(username=$username, password=***)"
}

/** Where a sending address's login is kept: Vault in production (api ADR-039), memory in dev and test. */
interface SendingLogins {
    fun write(
        addressId: Long,
        login: SmtpLogin,
    )

    /** The address's login, or null where none is kept. Throws [SendingLoginsUnavailable] when the store cannot be read. */
    fun read(addressId: Long): SmtpLogin?

    /** Removes the login and every earlier version of it. */
    fun delete(addressId: Long)
}

/** Vault KV at `secret/data/api/sending/<id>`, the one path the api may write. */
@Component
@ConditionalOnProperty("email.sending-logins", havingValue = "vault")
class VaultSendingLogins(
    private val vault: VaultTemplate,
) : SendingLogins {
    override fun write(
        addressId: Long,
        login: SmtpLogin,
    ) {
        val entry = mapOf("username" to login.username, "password" to login.password)
        reachable { vault.write("secret/data/api/sending/$addressId", mapOf("data" to entry)) }
    }

    override fun read(addressId: Long): SmtpLogin? =
        reachable {
            @Suppress("UNCHECKED_CAST")
            val data = vault.read("secret/data/api/sending/$addressId")?.data?.get("data") as? Map<String, Any?>
            data?.let { SmtpLogin(it["username"]?.toString().orEmpty(), it["password"]?.toString().orEmpty()) }
        }

    override fun delete(addressId: Long) {
        reachable { vault.delete("secret/metadata/api/sending/$addressId") }
    }

    private fun <T> reachable(call: () -> T): T =
        try {
            call()
        } catch (e: org.springframework.vault.VaultException) {
            throw SendingLoginsUnavailable().apply { initCause(e) }
        }
}

/** Dev and test keep logins in memory, under the same contract and protecting nothing. */
@Component
@ConditionalOnProperty("email.sending-logins", havingValue = "stand-in", matchIfMissing = true)
class LocalSendingLogins : SendingLogins {
    private val kept = ConcurrentHashMap<Long, SmtpLogin>()

    override fun write(
        addressId: Long,
        login: SmtpLogin,
    ) {
        kept[addressId] = login
    }

    override fun read(addressId: Long): SmtpLogin? = kept[addressId]

    override fun delete(addressId: Long) {
        kept.remove(addressId)
    }
}
