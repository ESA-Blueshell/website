package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.feature.telemetry.model.Redirect
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Redirect model test instances.
 */
@Component
class RedirectFactory(
    private val faker: Faker,
    private val telemetryFactory: TelemetryFactory
) {

    fun createBasic(): Redirect {
        val redirect = Redirect()
        val telemetry = telemetryFactory.createBasic()
        redirect.telemetry = telemetry
        return redirect
    }

    fun createFull(): Redirect = createBasic()

    fun createWithCustomizations(customizer: Consumer<Redirect>): Redirect {
        val redirect = createFull()
        customizer.accept(redirect)
        return redirect
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
