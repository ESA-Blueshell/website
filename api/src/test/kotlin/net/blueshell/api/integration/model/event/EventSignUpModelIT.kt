package net.blueshell.api.integration.model.event

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.survey.Answer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventSignUpModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_relations() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val guest = persist(guestFactory.createBasic())
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = persistAnswer(question)

            val signUp = eventSignUpFactory.createBasic()
            signUp.event = event
            signUp.user = user
            signUp.guest = guest
            val answers = signUp.answers as MutableSet<Answer>
            answers.clear()
            answers.add(answer)

            val found = persistAndReload(signUp, EventSignUp::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            assertEquals(user.id, found.userId)
            assertEquals(guest.id, found.guest?.id)
            assertEquals(1, found.answers.size)
        }
    }
}
