package net.blueshell.api.auth.domain.twofactor

import net.blueshell.api.auth.persistence.TrustedBrowser
import net.blueshell.api.auth.persistence.TrustedBrowserRepository
import net.blueshell.api.security.Browser
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

/**
 * Browsers that skip the sign-in code for thirty days from being trusted, however often they are
 * used. The cookie is `selector.verifier`; only the verifier's SHA-256 is stored, and each use
 * hands out a new verifier, so a copied cookie works at most once.
 */
@Service
class TrustedBrowsers(
    private val repository: TrustedBrowserRepository,
    private val users: UserService,
    private val clock: Clock,
) {
    data class Issued(
        val cookieValue: String,
        val ttl: Duration,
    )

    @Transactional
    fun trust(
        userId: Long,
        browser: Browser,
    ): Issued {
        val now = clock.instant()
        val verifier = random(32)
        val trusted =
            repository.save(
                TrustedBrowser(
                    user = users.findById(userId),
                    selector = random(16),
                    verifierHash = sha256(verifier),
                    browserFamily = browser.family,
                    browserPlatform = browser.platform,
                    trustedAt = now,
                    expiresAt = now.plus(LIFETIME),
                ),
            )
        return Issued("${trusted.selector}.$verifier", LIFETIME)
    }

    /** A new cookie for a trusted browser of [userId]'s own, or null when the cookie does not hold. */
    @Transactional
    fun redeem(
        cookieValue: String?,
        userId: Long,
        browser: Browser,
    ): Issued? {
        val (selector, verifier) = cookieValue?.split(".", limit = 2)?.takeIf { it.size == 2 } ?: return null
        val trusted = repository.findBySelector(selector).orElse(null) ?: return null
        val now = clock.instant()
        val holds =
            trusted.user.id == userId &&
                now.isBefore(trusted.expiresAt) &&
                Browser(trusted.browserFamily, trusted.browserPlatform) == browser &&
                MessageDigest.isEqual(sha256(verifier).toByteArray(), trusted.verifierHash.toByteArray())
        if (!holds) return null
        val next = random(32)
        trusted.verifierHash = sha256(next)
        trusted.lastUsedAt = now
        repository.save(trusted)
        return Issued("$selector.$next", Duration.between(now, trusted.expiresAt))
    }

    @Transactional(readOnly = true)
    fun of(userId: Long): List<TrustedBrowser> = repository.findOf(userId).filter { clock.instant().isBefore(it.expiresAt) }

    @Transactional
    fun forget(
        userId: Long,
        id: Long,
    ): Boolean {
        val trusted = repository.findById(id).orElse(null)?.takeIf { it.user.id == userId } ?: return false
        repository.purge(requireNotNull(trusted.id))
        return true
    }

    @Transactional
    fun forgetAll(userId: Long) {
        repository.purgeOf(userId)
    }

    private fun random(bytes: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(RANDOM::nextBytes))

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        val LIFETIME: Duration = Duration.ofDays(30)
        private val RANDOM = SecureRandom()
    }
}
