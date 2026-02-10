package net.blueshell.api.factory.model.event

import com.github.javafaker.Faker
import net.blueshell.api.event.persistence.EventSignUpAnswer
import net.blueshell.api.factory.model.survey.AnswerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for EventSignUpAnswer model test instances.
 */
@Component
class EventSignUpAnswerFactory(
    private val faker: Faker,
    private val eventSignUpFactory: EventSignUpFactory,
    private val answerFactory: AnswerFactory
) {

    fun createBasic(): EventSignUpAnswer {
        val signUpAnswer = EventSignUpAnswer()
        val signUp = eventSignUpFactory.createBasic()
        val answer = answerFactory.createBasic()
        signUpAnswer.eventSignUp = signUp
        signUpAnswer.answer = answer
        return signUpAnswer
    }

    fun createFull(): EventSignUpAnswer = createBasic()

    fun createWithCustomizations(customizer: Consumer<EventSignUpAnswer>): EventSignUpAnswer {
        val signUpAnswer = createFull()
        customizer.accept(signUpAnswer)
        return signUpAnswer
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
