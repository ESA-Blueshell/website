package net.blueshell.api.factory.model.event

import com.github.javafaker.Faker
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.survey.AnswerFactory
import net.blueshell.api.model.event.EventSignUp
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
        signUp.userId = user.id
        signUp.guest = null

        return signUp
    }

    fun createFull(): EventSignUp = createBasic()

    fun createWithCustomizations(customizer: Consumer<EventSignUp>): EventSignUp {
        val signUp = createFull()
        customizer.accept(signUp)
        return signUp
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
