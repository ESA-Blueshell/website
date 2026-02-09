package net.blueshell.api.factory.model.event

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.feature.event.model.EventSignUpAnswer
import net.blueshell.api.feature.survey.model.Answer
import org.junit.jupiter.api.Test

class EventSignUpAnswerFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event sign up answer`() {
        val event = persistEvent()
        val user = persistUser()
        val survey = persistSurvey()
        val question = persistQuestionWithSurvey(survey)
        val answer = persistTextAnswer(question)

        val signUp = eventSignUpFactory.createBasic()
        signUp.event = event
        signUp.user = user
        signUp.userId = user.id
        val signUpAnswers = signUp.answers as MutableSet<Answer>
        signUpAnswers.clear()
        val savedSignUp = persist(signUp)

        val signUpAnswer = eventSignUpAnswerFactory.createBasic()
        signUpAnswer.answer = answer
        signUpAnswer.eventSignUp = savedSignUp

        val savedAnswer = persist(signUpAnswer)
        assertPersisted(EventSignUpAnswer::class.java, savedAnswer.id)
    }
}
