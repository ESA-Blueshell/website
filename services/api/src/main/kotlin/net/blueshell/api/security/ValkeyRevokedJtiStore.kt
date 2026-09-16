package net.blueshell.api.security

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * The revoked ids, in the Valkey the sessions already live in.
 *
 * Valkey being unreachable is answered the way the cache and the session serializer answer it — by
 * degrading rather than by 500ing every authenticated request. The two directions fail differently
 * and deliberately:
 *
 * A read that cannot reach Valkey reports "not revoked". Reporting the opposite would turn one
 * unreachable Valkey into every reader on the site being signed out at once, which is a larger
 * failure than the one it would be guarding against.
 *
 * A write that cannot reach Valkey is logged at error and the sign-out carries on. The cookie is
 * still cleared and the session still dropped, so the reader is out; what is lost is the refusal of
 * a copy of that token taken beforehand. That is worth an alert, which is what the level is for.
 */
@Component
class ValkeyRevokedJtiStore(
    private val redis: StringRedisTemplate
) : RevokedJtiStore {

    private val log = LoggerFactory.getLogger(ValkeyRevokedJtiStore::class.java)

    override fun add(jti: String, ttl: Duration) {
        if (ttl.isZero || ttl.isNegative) return
        try {
            redis.opsForValue().set(key(jti), "1", ttl)
        } catch (e: DataAccessException) {
            log.error("Could not record the revocation of jti={}; the token stands until it expires", jti, e)
        }
    }

    override fun contains(jti: String): Boolean =
        try {
            redis.hasKey(key(jti))
        } catch (e: DataAccessException) {
            log.warn("Could not read the revocation of jti={}; treating it as live", jti, e)
            false
        }

    private fun key(jti: String) = "$KEY_PREFIX$jti"

    companion object {
        /** Namespaced alongside `blueshell-api`, the sessions' own namespace. */
        const val KEY_PREFIX = "blueshell-api:jwt:revoked:"
    }
}
