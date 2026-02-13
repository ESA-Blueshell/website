package net.blueshell.api.factory.dto

import com.github.javafaker.Faker
import jakarta.persistence.MappedSuperclass
import org.springframework.security.crypto.password.NoOpPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Base support for DTO factories to produce reproducible test fixtures.
 */
@MappedSuperclass
abstract class BaseDtoFactory<T>(
    protected val faker: Faker,
    protected val passwordEncoder: PasswordEncoder,
    protected val random: Random
) {

    protected constructor() : this(Faker(), NoOpPasswordEncoder.getInstance(), Random())

    /** The DTO class this factory produces; used by the registry. */
    abstract fun targetType(): Class<T>

    /** Minimal valid instance. */
    abstract fun createBasic(): T

    /** Fully populated instance (defaults to basic). */
    open fun createFull(): T = createBasic()

    /** Instance with inline tweaks. */
    fun createWithCustomizations(customizer: Consumer<T>?): T {
        val t = createFull()
        customizer?.accept(t)
        return t
    }

    protected fun nextId(): Long = SEQ.incrementAndGet()

    protected fun unique(prefix: String): String = "$prefix-${nextId()}"

    protected fun email(local: String): String = ("$local+${nextId()}@test.com").lowercase()

    protected fun now(): Instant = Instant.now().atZone(ZoneId.of("Europe/Amsterdam")).toInstant()

    protected fun today(): LocalDate = LocalDate.now(ZoneId.of("Europe/Amsterdam"))

    private companion object {
        val SEQ = AtomicLong(1000)
    }
}
