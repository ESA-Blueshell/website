package net.blueshell.api.file.api

import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The site's own account, which every picture the site stores by itself is credited to, and how
 * such a store fails: logged and skipped, because the record the picture was for stands without
 * it. [ShippedPictures] and [SitePictures] both go through here.
 */
@Component
class SiteAccount(
    private val users: UserService,
) {
    /**
     * The account, or null where the migration that writes it has not run, which is logged rather
     * than thrown for [what].
     */
    fun orNull(what: String): User? {
        val account = runCatching { users.findByUsername(NAME) }.getOrNull()
        if (account == null) log.warn("[site-pictures] there is no '{}' account to credit {} to", NAME, what)
        return account
    }

    /** What [store] answers, or null where it threw, which is logged for [what]. */
    @Suppress("TooGenericExceptionCaught") // Any failure skips this one picture; none may fail the caller.
    fun <T> attempt(
        what: String,
        store: () -> T,
    ): T? =
        try {
            store()
        } catch (e: Exception) {
            log.warn("[site-pictures] could not store {}: {}", what, e.message)
            null
        }

    private companion object {
        val log = LoggerFactory.getLogger(SiteAccount::class.java)
        const val NAME = "system"
    }
}
