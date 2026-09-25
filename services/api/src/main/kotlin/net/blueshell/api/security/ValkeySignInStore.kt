package net.blueshell.api.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Sign-ins as Valkey hashes, so a rotation can compare and replace the token id in one step and
 * every replica sees the same record. An unreachable Valkey throws: a sign-in whose state cannot
 * be read is not trusted (api ADR-030).
 */
@Component
class ValkeySignInStore(
    private val redis: StringRedisTemplate,
) : SignInStore {
    override fun save(signIn: SignIn, expiresAt: Instant) {
        val key = key(signIn.id)
        redis.opsForHash<String, String>().putAll(key, fieldsOf(signIn))
        signIn.previousJti ?: redis.opsForHash<String, String>().delete(key, PREVIOUS_JTI, PREVIOUS_RETIRED_AT)
        signIn.steppedUpAt ?: redis.opsForHash<String, String>().delete(key, STEPPED_UP_AT)
        redis.expireAt(key, expiresAt)
        redis.opsForSet().add(indexKey(signIn.userId), signIn.id)
    }

    override fun find(id: String): SignIn? {
        val fields = redis.opsForHash<String, String>().entries(key(id))
        if (fields.isEmpty()) return null
        return signInOf(id, fields)
    }

    override fun delete(id: String) {
        val userId = redis.opsForHash<String, String>().get(key(id), USER_ID)
        redis.delete(key(id))
        userId?.let { redis.opsForSet().remove(indexKey(it.toLong()), id) }
    }

    override fun unindex(userId: Long, id: String) {
        redis.opsForSet().remove(indexKey(userId), id)
    }

    override fun idsOf(userId: Long): Set<String> = redis.opsForSet().members(indexKey(userId)).orEmpty()

    override fun rotate(id: String, expectedJti: String, newJti: String, at: Instant, expiresAt: Instant): Boolean {
        val result =
            redis.execute(
                ROTATE,
                listOf(key(id)),
                expectedJti,
                newJti,
                at.toEpochMilli().toString(),
                expiresAt.toEpochMilli().toString(),
            )
        return result == 1L
    }

    override fun securityStamp(userId: Long): Long = redis.opsForValue().get(stampKey(userId))?.toLong() ?: 0

    override fun bumpSecurityStamp(userId: Long): Long = redis.opsForValue().increment(stampKey(userId)) ?: 0

    private fun fieldsOf(signIn: SignIn): Map<String, String> =
        buildMap {
            put(USER_ID, signIn.userId.toString())
            put(STARTED_AT, signIn.startedAt.toEpochMilli().toString())
            put(LAST_SEEN_AT, signIn.lastSeenAt.toEpochMilli().toString())
            put(BROWSER_FAMILY, signIn.browser.family)
            put(BROWSER_PLATFORM, signIn.browser.platform)
            put(SECURITY_STAMP, signIn.securityStamp.toString())
            put(CURRENT_JTI, signIn.currentJti)
            put(CURRENT_ISSUED_AT, signIn.currentIssuedAt.toEpochMilli().toString())
            signIn.previousJti?.let { put(PREVIOUS_JTI, it) }
            signIn.previousRetiredAt?.let { put(PREVIOUS_RETIRED_AT, it.toEpochMilli().toString()) }
            signIn.steppedUpAt?.let { put(STEPPED_UP_AT, it.toEpochMilli().toString()) }
            put(METHODS, signIn.methods.joinToString(","))
        }

    // A null for each field a hash written before a field existed can lack.
    @Suppress("ReturnCount")
    private fun signInOf(id: String, fields: Map<String, String>): SignIn? {
        fun instant(name: String): Instant? = fields[name]?.toLongOrNull()?.let(Instant::ofEpochMilli)
        return SignIn(
            id = id,
            userId = fields[USER_ID]?.toLongOrNull() ?: return null,
            startedAt = instant(STARTED_AT) ?: return null,
            lastSeenAt = instant(LAST_SEEN_AT) ?: return null,
            browser = Browser(fields[BROWSER_FAMILY].orEmpty(), fields[BROWSER_PLATFORM].orEmpty()),
            securityStamp = fields[SECURITY_STAMP]?.toLongOrNull() ?: return null,
            currentJti = fields[CURRENT_JTI] ?: return null,
            currentIssuedAt = instant(CURRENT_ISSUED_AT) ?: return null,
            previousJti = fields[PREVIOUS_JTI],
            previousRetiredAt = instant(PREVIOUS_RETIRED_AT),
            steppedUpAt = instant(STEPPED_UP_AT),
            methods = fields[METHODS].orEmpty().split(",").filter { it.isNotBlank() }.toSet(),
        )
    }

    companion object {
        const val KEY_PREFIX = "blueshell-api:sign-in:"
        private const val INDEX_PREFIX = "blueshell-api:sign-ins-of:"
        private const val STAMP_PREFIX = "blueshell-api:security-stamp:"

        private const val USER_ID = "userId"
        private const val STARTED_AT = "startedAt"
        private const val LAST_SEEN_AT = "lastSeenAt"
        private const val BROWSER_FAMILY = "browserFamily"
        private const val BROWSER_PLATFORM = "browserPlatform"
        private const val SECURITY_STAMP = "securityStamp"
        private const val CURRENT_JTI = "currentJti"
        private const val CURRENT_ISSUED_AT = "currentIssuedAt"
        private const val PREVIOUS_JTI = "previousJti"
        private const val PREVIOUS_RETIRED_AT = "previousRetiredAt"
        private const val STEPPED_UP_AT = "steppedUpAt"
        private const val METHODS = "methods"

        private fun key(id: String) = "$KEY_PREFIX$id"

        private fun indexKey(userId: Long) = "$INDEX_PREFIX$userId"

        private fun stampKey(userId: Long) = "$STAMP_PREFIX$userId"

        private val ROTATE =
            DefaultRedisScript(
                """
                if redis.call('HGET', KEYS[1], '$CURRENT_JTI') ~= ARGV[1] then return 0 end
                redis.call('HSET', KEYS[1],
                    '$PREVIOUS_JTI', ARGV[1], '$PREVIOUS_RETIRED_AT', ARGV[3],
                    '$CURRENT_JTI', ARGV[2], '$CURRENT_ISSUED_AT', ARGV[3], '$LAST_SEEN_AT', ARGV[3])
                redis.call('PEXPIREAT', KEYS[1], ARGV[4])
                return 1
                """.trimIndent(),
                Long::class.javaObjectType,
            )
    }
}
