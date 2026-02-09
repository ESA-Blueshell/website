package net.blueshell.api.factory.model.event

import com.github.javafaker.Faker
import net.blueshell.api.event.domain.model.EventFeedback
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for EventFeedback model test instances.
 */
@Component
class EventFeedbackFactory(
    private val faker: Faker,
    private val eventFactory: EventFactory
) {

    fun createBasic(): EventFeedback {
        val feedback = EventFeedback()
        feedback.event = eventFactory.createBasic()
        feedback.feedback = faker.lorem().sentence(10)
        return feedback
    }

    fun createFull(): EventFeedback = createBasic()

    fun createWithCustomizations(customizer: Consumer<EventFeedback>): EventFeedback {
        val feedback = createFull()
        customizer.accept(feedback)
        return feedback
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
