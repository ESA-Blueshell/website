package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.shared.enums.PlatformType
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Telemetry model test instances.
 */
@Component
class TelemetryFactory(
    private val faker: Faker
) {

    fun createBasic(): Telemetry {
        val telemetry = Telemetry()
        telemetry.platform = faker.options().option(PlatformType::class.java)
        telemetry.url = faker.internet().url()
        return telemetry
    }

    fun createFull(): Telemetry = createBasic()

    fun createWithCustomizations(customizer: Consumer<Telemetry>): Telemetry {
        val telemetry = createFull()
        customizer.accept(telemetry)
        return telemetry
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
