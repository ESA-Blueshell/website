package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.shared.enums.ResetType
import net.blueshell.api.auth.model.RecoveryToken
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for RecoveryToken model test instances.
 */
@Component
class RecoveryTokenFactory(
    private val faker: Faker,
    private val userFactory: UserFactory
) {

    fun createBasic(): RecoveryToken {
        val token = RecoveryToken()
        val user = userFactory.createBasic()
        token.user = user
        token.type = faker.options().option(ResetType::class.java)
        token.selector = faker.crypto().sha256().substring(0, 32)
        token.verifierHash = faker.crypto().sha256()
        token.expiresAt = Instant.now().plus(2, ChronoUnit.HOURS)
        token.consumedAt = null
        return token
    }

    fun createFull(): RecoveryToken = createBasic()

    fun createWithCustomizations(customizer: Consumer<RecoveryToken>): RecoveryToken {
        val token = createFull()
        customizer.accept(token)
        return token
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
