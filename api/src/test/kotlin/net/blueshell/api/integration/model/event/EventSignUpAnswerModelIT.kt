package net.blueshell.api.integration.model.event

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.event.EventSignUpAnswer
import net.blueshell.api.model.survey.Answer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventSignUpAnswerModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_join_columns() {
            val event = persistEvent()
            val user = persist(userFactory.createBasic())
            val survey = persistSurvey()
            val question = persistQuestionWithSurvey(survey)
            val answer = persistAnswer(question)

            val signUp = eventSignUpFactory.createBasic()
            signUp.event = event
            signUp.user = user
            val answers = signUp.answers as MutableSet<Answer>
            answers.clear()
            val savedSignUp = persist(signUp)

            val signUpAnswer = eventSignUpAnswerFactory.createBasic()
            signUpAnswer.eventSignUp = savedSignUp
            signUpAnswer.answer = answer

            val found = persistAndReload(signUpAnswer, EventSignUpAnswer::class.java) { it.id }

            assertEquals(savedSignUp.id, found.eventSignUpId)
            assertEquals(answer.id, found.answerId)
        }
    }
}
