package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.model.base.BaseModel
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Base class for model factories.
 */
abstract class BaseFactory<T>(
    protected val faker: Faker,
    protected val passwordEncoder: PasswordEncoder,
    protected val random: Random
) {

    /** Create a basic instance with minimal required fields. */
    abstract fun createBasic(): T

    /** Create a fully populated instance. */
    abstract fun createFull(): T

    /** Create an instance with specific customizations. */
    abstract fun createWithCustomizations(customizer: Consumer<T>): T

    protected fun generateId(): Long = ID_COUNTER.incrementAndGet()

    protected fun futureInstant(): Instant {
        return LocalDateTime.now().plusDays(faker.number().numberBetween(1, 365).toLong())
            .atZone(ZoneId.systemDefault()).toInstant()
    }

    protected fun pastInstant(): Instant {
        return LocalDateTime.now().minusDays(faker.number().numberBetween(1, 365).toLong())
            .atZone(ZoneId.systemDefault()).toInstant()
    }

    protected fun futureDate(): LocalDate = LocalDate.now().plusDays(faker.number().numberBetween(1, 365).toLong())

    protected fun pastDate(): LocalDate = LocalDate.now().minusDays(faker.number().numberBetween(1, 365).toLong())

    /** Hook to set base entity fields if needed (usually set by JPA). */
    protected fun <M : BaseModel> setBaseFields(entity: M) {
        // Intentionally no-op for tests
    }

    private companion object {
        val ID_COUNTER = AtomicLong(1000)
    }
}
