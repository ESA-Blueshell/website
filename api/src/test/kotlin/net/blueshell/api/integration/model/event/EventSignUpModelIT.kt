package net.blueshell.api.integration.model.event

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.event.EventSignUp
import net.blueshell.api.model.event.Guest
import net.blueshell.api.model.survey.Answer
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
            signUp.user = user

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            assertEquals(event.id, found.event.id)
        }

        @Test
        fun `persists event relation when setting id`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())

            val signUp = EventSignUp()
            signUp.eventId = event.id!!
            signUp.user = user

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            assertEquals(event.id, found.event.id)
        }

        @Test
        fun `persists user relation when setting entity`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())

            val signUp = EventSignUp()
            signUp.event = event
            signUp.user = user

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user?.id)
        }

        @Test
        fun `persists user relation when setting id`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())

            val signUp = EventSignUp()
            signUp.event = event
            signUp.user = null
            signUp.userId = user.id

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(user.id, found.userId)
            assertEquals(user.id, found.user?.id)
        }

        @Test
        fun `clears user relation when setting id to null`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val guest = persist(guestFactory.createBasic())

            val signUp = EventSignUp()
            signUp.event = event
            signUp.user = user
            signUp.userId = null
            signUp.guest = guest

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(null, found.userId)
            assertEquals(null, found.user)
            assertEquals(guest.id, found.guest?.id)
        }

        @Test
        fun `persists guest relation when setting entity`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val guest = persist(guestFactory.createBasic())

            val signUp = EventSignUp()
            signUp.event = event
            signUp.user = user
            signUp.guest = guest

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(guest.id, found.guest?.id)
        }

        @Test
        fun `persists guest relation when setting id`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val guest = persist(guestFactory.createBasic())

            val signUp = EventSignUp()
            signUp.event = event
            signUp.user = user
            signUp.guest = entityManager.getReference(Guest::class.java, guest.id)

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(guest.id, found.guest?.id)
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
            signUp.user = user
            val answers = signUp.answers as MutableSet<Answer>
            answers.clear()
            answers.add(answerOne)
            answers.add(answerTwo)

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(2, found.answers.size)
        }

        @Test
        fun `persists answers relation when setting id`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answerOne = persistAnswer(question)
            val answerTwo = persistAnswer(question)

            val signUp = EventSignUp()
            signUp.event = event
            signUp.user = user
            val answers = signUp.answers as MutableSet<Answer>
            answers.clear()
            answers.add(entityManager.getReference(Answer::class.java, answerOne.id))
            answers.add(entityManager.getReference(Answer::class.java, answerTwo.id))

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(2, found.answers.size)
        }
    }
}
