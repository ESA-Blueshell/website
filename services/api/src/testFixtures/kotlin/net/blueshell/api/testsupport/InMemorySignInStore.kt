package net.blueshell.api.testsupport

import net.blueshell.api.security.SignIn
import net.blueshell.api.security.SignInStore
import java.time.Instant

/** A [SignInStore] in a map, for unit tests of the rules around it. Expiry is left to the rules. */
class InMemorySignInStore : SignInStore {
    private val signIns = linkedMapOf<String, SignIn>()
    private val stamps = mutableMapOf<Long, Long>()

    override fun save(signIn: SignIn, expiresAt: Instant) {
        signIns[signIn.id] = signIn
    }

    override fun find(id: String): SignIn? = signIns[id]

    override fun delete(id: String) {
        signIns.remove(id)
    }

    override fun unindex(userId: Long, id: String) = Unit

    override fun idsOf(userId: Long): Set<String> = signIns.values.filter { it.userId == userId }.map { it.id }.toSet()

    override fun rotate(id: String, expectedJti: String, newJti: String, at: Instant, expiresAt: Instant): Boolean {
        val signIn = signIns[id]?.takeIf { it.currentJti == expectedJti } ?: return false
        signIns[id] =
            signIn.copy(
                previousJti = expectedJti,
                previousRetiredAt = at,
                currentJti = newJti,
                currentIssuedAt = at,
                lastSeenAt = at,
            )
        return true
    }

    override fun securityStamp(userId: Long): Long = stamps[userId] ?: 0

    override fun bumpSecurityStamp(userId: Long): Long = (securityStamp(userId) + 1).also { stamps[userId] = it }
}
