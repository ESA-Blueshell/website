package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.user.domain.model.Address
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Address model test instances.
 */
@Component
class AddressFactory(
    private val faker: Faker
) {

    fun createBasic(): Address {
        val address = Address()
        address.country = "Netherlands"
        address.city = faker.address().city()
        address.street = faker.address().streetName()
        address.houseNumber = faker.number().numberBetween(1, 999).toString()
        address.zipCode = generateDutchZipCode()
        return address
    }

    fun createFull(): Address = createBasic()

    fun createWithCustomizations(customizer: Consumer<Address>): Address {
        val address = createFull()
        customizer.accept(address)
        return address
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private fun generateDutchZipCode(): String {
        return faker.number().numberBetween(1000, 9999).toString() + " " + faker.letterify("??").uppercase()
    }

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
