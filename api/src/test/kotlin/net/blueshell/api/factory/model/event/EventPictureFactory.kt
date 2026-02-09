package net.blueshell.api.factory.model.event

import com.github.javafaker.Faker
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.event.persistence.EventPicture
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for EventPicture model test instances.
 */
@Component
class EventPictureFactory(
    private val faker: Faker,
    private val eventFactory: EventFactory,
    private val fileFactory: FileFactory
) {

    fun createBasic(): EventPicture {
        val picture = EventPicture()
        picture.event = eventFactory.createBasic()
        picture.picture = fileFactory.createImage()
        return picture
    }

    fun createFull(): EventPicture = createBasic()

    fun createWithCustomizations(customizer: Consumer<EventPicture>): EventPicture {
        val picture = createFull()
        customizer.accept(picture)
        return picture
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
