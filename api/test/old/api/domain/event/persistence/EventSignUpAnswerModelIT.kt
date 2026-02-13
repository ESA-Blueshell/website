package net.blueshell.api.domain.event.persistence

import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventSignUpAnswerModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists event sign-up relation when setting entity`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = persistAnswer(question)

            val signUp = eventSignUpFactory.createBasic()
            signUp.event = event
            signUp.userId = user.id
            val answers = signUp.answers as MutableSet<Answer>
            answers.clear()
            val savedSignUp = persist(signUp)

            val signUpAnswer = eventSignUpAnswerFactory.createBasic()
            signUpAnswer.eventSignUp = savedSignUp
            signUpAnswer.answer = answer

            val found = persistAndReload(signUpAnswer, EventSignUpAnswer::class.java) { it.id }

            assertEquals(savedSignUp.id, found.eventSignUpId)
            Assertions.assertEquals(savedSignUp.id, found.eventSignUp.id)
        }

        @Test
        fun `persists answer relation when setting entity`() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = persistAnswer(question)

            val signUp = eventSignUpFactory.createBasic()
            signUp.event = event
            signUp.userId = user.id
            val answers = signUp.answers as MutableSet<Answer>
            answers.clear()
            val savedSignUp = persist(signUp)

            val signUpAnswer = eventSignUpAnswerFactory.createBasic()
            signUpAnswer.eventSignUp = savedSignUp
            signUpAnswer.answer = answer

            val found = persistAndReload(signUpAnswer, EventSignUpAnswer::class.java) { it.id }

            assertEquals(answer.id, found.answerId)
            Assertions.assertEquals(answer.id, found.answer.id)
        }
    }
}
