package net.blueshell.api.domain.event.persistence

import net.blueshell.api.domain.event.web.mapping.asDto
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventSignUpModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists event relation when setting entity`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())

            val signUp = EventSignUp()
            signUp.event = event
            signUp.userId = user.id

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            Assertions.assertEquals(event.id, found.event.id)
        }

        @Test
        fun `persists user relation when setting entity`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())

            val signUp = EventSignUp()
            signUp.event = event
            signUp.userId = user.id

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            Assertions.assertEquals(user.id, found.userId)
            Assertions.assertEquals(user.id, found.user?.id)
        }

        @Test
        fun `persists guest relation when setting entity`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val guest = persist(guestFactory.createBasic())

            val signUp = EventSignUp()
            signUp.event = event
            signUp.userId = user.id
            signUp.guest = guest

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            Assertions.assertEquals(guest.id, found.guest?.id)
        }

        @Test
        fun `persists answers relation when setting entity`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answerOne = persistAnswer(question)
            val answerTwo = persistAnswer(question)

            val signUp = EventSignUp()
            signUp.event = event
            signUp.userId = user.id
            val answers = signUp.answers as MutableSet<Answer>
            answers.clear()
            answers.add(answerOne)
            answers.add(answerTwo)

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(2, found.answers.size)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted sign up`() {
            var event = persistEvent()
            event.signUpForm = persist(surveyFactory.createFull())
            event = persist(event)

            val user = persist(userFactory.createBasic())
            val signUp = persist(eventSignUpFactory.createForEventAndUser(event, user))

            val dto = signUp.asDto()

            assertEquals(signUp.id, dto.id)
            assertEquals(signUp.eventId, dto.eventId)
            assertEquals(signUp.userId, dto.userId)
            assertEquals(signUp.answers.size, dto.answers!!.size)
        }
    }
}
