package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The service account: the user the site itself acts as, never a person and never signed in to
 * (api ADR-028). It is found by holding SYSTEM, the same test `UserPrincipal.isServiceAccount`
 * makes, not by its username.
 */
@Component
class ServiceAccount(
    private val users: UserRepository,
) {
    /** The account, or null where the database has none, which is logged for [purpose]. */
    fun orNull(purpose: String): User? {
        val account = users.findServiceAccounts().firstOrNull()
        if (account == null) log.warn("[service-account] there is no service account to credit {} to", purpose)
        return account
    }

    private companion object {
        val log = LoggerFactory.getLogger(ServiceAccount::class.java)
    }
}
