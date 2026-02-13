package net.blueshell.api.factory.model.event

import com.github.javafaker.Faker
import net.blueshell.api.domain.event.persistence.EventBanner
import net.blueshell.api.factory.model.FileFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for EventBanner model test instances.
 */
@Component
class EventBannerFactory(
    private val faker: Faker,
    private val eventFactory: EventFactory,
    private val fileFactory: FileFactory
) {

    fun createBasic(): EventBanner {
        val banner = EventBanner()
        val event = eventFactory.createBasic()
        val file = fileFactory.createImage()
        banner.event = event
        banner.id.fileId = file.id
        return banner
    }

    fun createFull(): EventBanner = createBasic()

    fun createWithCustomizations(customizer: Consumer<EventBanner>): EventBanner {
        val banner = createFull()
        customizer.accept(banner)
        return banner
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
