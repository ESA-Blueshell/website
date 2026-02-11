package net.blueshell.api.factory.model.event

import com.github.javafaker.Faker
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.survey.AnswerFactory
import net.blueshell.api.domain.user.persistence.User
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for EventSignUp model test instances.
 */
@Component
class EventSignUpFactory(
    private val faker: Faker,
    private val eventFactory: EventFactory,
    private val userFactory: UserFactory,
    private val answerFactory: AnswerFactory
) {

    fun createBasic(): EventSignUp {
        val signUp = EventSignUp()

        val event = eventFactory.createFull()
        val user = userFactory.createFull()

        signUp.event = event
        signUp.user = user
        signUp.guest = null

        return signUp
    }

    fun createFull(): EventSignUp = createBasic()

    fun createWithCustomizations(customizer: Consumer<EventSignUp>): EventSignUp {
        val signUp = createFull()
        customizer.accept(signUp)
        return signUp
    }

    fun createForEventAndUser(event: Event, user: User): EventSignUp {
        val signUp = EventSignUp()
        signUp.event = event
        signUp.user = user
        signUp.guest = null
        return signUp
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
