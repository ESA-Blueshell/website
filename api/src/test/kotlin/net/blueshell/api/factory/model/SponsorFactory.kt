package net.blueshell.api.factory.model

import com.github.javafaker.Faker
import net.blueshell.api.sponsor.domain.model.Sponsor
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Sponsor model test instances.
 */
@Component
class SponsorFactory(
    private val faker: Faker,
    private val fileFactory: FileFactory
) {

    fun createBasic(): Sponsor {
        val sponsor = Sponsor()
        sponsor.name = faker.company().name()
        sponsor.description = faker.lorem().paragraph(3)
        sponsor.picture = fileFactory.createImage()
        return sponsor
    }

    fun createFull(): Sponsor = createBasic()

    fun createWithCustomizations(customizer: Consumer<Sponsor>): Sponsor {
        val sponsor = createFull()
        customizer.accept(sponsor)
        return sponsor
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
