package net.blueshell.api.event.persistence

import net.blueshell.api.event.web.asResponse
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Guards the `EventSignUp.withGuestUserAndAnswers` entity graph against N+1 regressions.
 *
 * Mapping each sign-up to a response touches its user, guest and answers. The graph must
 * fetch all three eagerly, so the statement count for `findByEvent_Id` stays constant no
 * matter how many sign-ups an event has.
 */
@SpringBootTest
class EventSignUpFetchIT : UserTestSupport() {

    @Autowired
    private lateinit var eventSignUps: EventSignUpRepository

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    private val sessionFactory: SessionFactory
        get() = entityManagerFactory.unwrap(SessionFactory::class.java)

    @Test
    fun `listing sign-ups for an event does not scale queries with the number of sign-ups`() {
        val question = persistQuestion()
        val singleSignUpEvent = createEventFixture().also { seedMemberSignUp(it, question) }
        val manySignUpsEvent = createEventFixture().also { event ->
            repeat(5) { seedMemberSignUp(event, question) }
        }

        // Prime the persistence unit so one-time, session-level statements don't skew the counts.
        mapSignUps(singleSignUpEvent.id!!)
        mapSignUps(manySignUpsEvent.id!!)

        val queriesForOne = countStatements { mapSignUps(singleSignUpEvent.id!!) }
        val queriesForMany = countStatements { mapSignUps(manySignUpsEvent.id!!) }

        assertThat(mapSignUps(singleSignUpEvent.id!!)).hasSize(1)
        assertThat(mapSignUps(manySignUpsEvent.id!!)).hasSize(5)
        // An N+1 adds one statement per extra sign-up; tolerate a single statement of
        // session-level variance but reject growth proportional to the sign-up count.
        assertThat(queriesForMany)
            .describedAs("query count must not grow with the number of sign-ups (N+1)")
            .isLessThanOrEqualTo(queriesForOne + 1)
    }

    private fun persistQuestion(): Question {
        val survey = persist(Survey())
        return persist(Question(idx = 0, survey = survey, type = QuestionType.OPEN, label = "Why?"))
    }

    private fun seedMemberSignUp(event: Event, question: Question) {
        val user = createUserWithRole(Role.MEMBER)
        val signUp = eventFactory.createSignUp(event, user)
        (signUp.answers as MutableSet).add(Answer(question = question, textResponse = "because"))
        persist(signUp)
    }

    /** Loads the sign-ups for an event and fully maps them, forcing every association to initialise. */
    private fun mapSignUps(eventId: Long) =
        transactionTemplate.execute {
            eventSignUps.findByEvent_Id(eventId).map { it.asResponse() }
        }!!

    private fun countStatements(block: () -> Unit): Long {
        sessionFactory.statistics.isStatisticsEnabled = true
        sessionFactory.statistics.clear()
        block()
        return sessionFactory.statistics.prepareStatementCount
    }
}
