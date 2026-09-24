package net.blueshell.api.security

import java.time.Instant

interface SignInStore {
    fun save(signIn: SignIn, expiresAt: Instant)

    fun find(id: String): SignIn?

    fun delete(id: String)

    /** Drops [id] from the person's index, for a sign-in the store has already let expire. */
    fun unindex(userId: Long, id: String)

    /** The ids of every sign-in the person holds, live or already expired from the store. */
    fun idsOf(userId: Long): Set<String>

    /** Replaces the current token id only if it is still [expectedJti], so two rotations cannot race. */
    fun rotate(id: String, expectedJti: String, newJti: String, at: Instant, expiresAt: Instant): Boolean

    fun securityStamp(userId: Long): Long

    fun bumpSecurityStamp(userId: Long): Long
}
