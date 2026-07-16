package net.blueshell.api.platform.config

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.cache.RedisCacheConfiguration
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.time.Duration

/**
 * A Serializable payload the [ReloadingClassLoader] can redefine, so a
 * deserialized instance can be attributed to a specific classloader.
 */
class CachedStub(val value: Int) : Serializable

/**
 * Loads [CachedStub] itself (child-first) instead of delegating to its parent,
 * producing a Class instance distinct from the one the application classloader
 * holds — the same classloader split that Spring Boot DevTools' RestartClassLoader
 * creates in development.
 */
private class ReloadingClassLoader(parent: ClassLoader) : ClassLoader(parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            if (name == CachedStub::class.java.name) {
                val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")?.readBytes()
                if (bytes != null) {
                    val defined = defineClass(name, bytes, 0, bytes.size)
                    if (resolve) resolveClass(defined)
                    return defined
                }
            }
            return super.loadClass(name, resolve)
        }
    }
}

class CacheConfigTest {

    private fun serialize(value: Serializable): ByteArray =
        ByteArrayOutputStream().use { bos ->
            ObjectOutputStream(bos).use { it.writeObject(value) }
            bos.toByteArray()
        }

    @Test
    fun `value deserialization resolves classes with the configured classloader`() {
        // Bytes written by the ordinary application/test classloader.
        val bytes = serialize(CachedStub(42))
        val reloading = ReloadingClassLoader(javaClass.classLoader)

        // Pinning the config to the reloading loader (what CacheConfig does with
        // its own classloader) makes deserialization resolve the value against
        // that loader — this is what keeps a cached principal usable after a
        // DevTools restart.
        val pinned = RedisCacheConfiguration.defaultCacheConfig(reloading)
            .valueSerializationPair
            .read(ByteBuffer.wrap(bytes))
        assertThat(pinned!!.javaClass.classLoader).isSameAs(reloading)

        // The default (no classloader) config resolves against the loader on the
        // stack instead — the exact behaviour that produced the ClassCastException.
        val default = RedisCacheConfiguration.defaultCacheConfig()
            .valueSerializationPair
            .read(ByteBuffer.wrap(bytes))
        assertThat(default!!.javaClass.classLoader).isNotSameAs(reloading)
    }

    @Test
    fun `cache configuration round-trips a UserPrincipal through its value serializer`() {
        val config = CacheConfig(Duration.ofMinutes(5)).cacheConfiguration()
        val principal = UserPrincipal(
            id = 1L,
            usernameValue = "alice",
            passwordValue = "secret",
            enabledValue = true,
            roles = setOf(Role.MEMBER),
            addressId = null,
            personDetailsId = null,
        )

        val pair = config.valueSerializationPair
        val restored = pair.read(pair.write(principal))

        assertThat(restored).isEqualTo(principal)
        assertThat(restored!!.javaClass.classLoader).isSameAs(CacheConfig::class.java.classLoader)
    }
}
