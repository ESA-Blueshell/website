package net.blueshell.api.factory.model.event

import com.github.javafaker.Faker
import net.blueshell.api.feature.event.model.Guest
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Guest model test instances.
 */
@Component
class GuestFactory(
    private val faker: Faker
) {

    fun createBasic(): Guest {
        val guest = Guest()
        guest.name = faker.name().fullName()
        guest.discord = faker.name().username() + "#" + faker.number().numberBetween(1000, 9999)
        guest.email = faker.internet().emailAddress()
        guest.phoneNumber = faker.phoneNumber().phoneNumber()
        guest.accessToken = UUID.randomUUID().toString()
        return guest
    }

    fun createFull(): Guest = createBasic()

    fun createWithCustomizations(customizer: Consumer<Guest>): Guest {
        val guest = createFull()
        customizer.accept(guest)
        return guest
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
