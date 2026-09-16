package net.blueshell.api.security

import java.time.Duration

/**
 * Where a retired token id is written down.
 *
 * Revocation has to outlive the pod that performed it: signing out on one replica means nothing if
 * the next request lands on another and finds a set that was never told. It also has to outlive a
 * restart, which a thirty-day token comfortably does.
 *
 * Entries carry the retired token's own remaining life. Past that the token is refused for being
 * expired, so keeping the id any longer records nothing.
 */
interface RevokedJtiStore {
    fun add(jti: String, ttl: Duration)

    fun contains(jti: String): Boolean
}
