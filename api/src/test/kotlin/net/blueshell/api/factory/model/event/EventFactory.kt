package net.blueshell.api.factory.model.event

import com.github.javafaker.Faker
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.survey.SurveyFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Event model test instances.
 */
@Component
class EventFactory(
    private val faker: Faker,
    private val committeeFactory: CommitteeFactory,
    private val surveyFactory: SurveyFactory
) {

    fun createBasic(): Event {
        val event = Event()
        event.title = faker.book().title() + " Event"
        event.description = faker.lorem().paragraph(5)
        event.location = faker.address().fullAddress()
        event.startTime = Instant.now().plus(7, ChronoUnit.DAYS)
        event.endTime = Instant.now().plus(8, ChronoUnit.DAYS)
        event.approved = faker.bool().bool()
        event.membersOnly = faker.bool().bool()
        event.signUp = faker.bool().bool()

        val committee = committeeFactory.createBasic()
        event.committee = committee
        return event
    }

    fun createFull(): Event {
        val event = createBasic()
        event.memberPrice = faker.number().randomDouble(2, 0, 50)
        event.publicPrice = faker.number().randomDouble(2, 0, 100)
        event.googleId = faker.internet().uuid()

        if (event.signUp) {
            val survey = surveyFactory.createBasic()
            event.signUpForm = survey
            event.signUpFormId = survey.id
        }
        return event
    }

    fun createWithCustomizations(customizer: Consumer<Event>): Event {
        val event = createFull()
        customizer.accept(event)
        return event
    }

    fun createApproved(): Event {
        return createWithCustomizations { it.approved = true }
    }

    fun createWithSignUp(): Event {
        return createWithCustomizations { event ->
            event.signUp = true
            val survey = surveyFactory.createBasic()
            event.signUpForm = survey
            event.signUpFormId = survey.id
        }
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
