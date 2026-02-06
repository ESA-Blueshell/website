package net.blueshell.api.factory.model.event

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.model.event.EventSignUp
import net.blueshell.api.model.survey.Answer
import org.junit.jupiter.api.Test

class EventSignUpFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable event sign up`() {
        val event = persistEvent()
        val user = persistUser()
        val survey = persistSurvey()
        val question = persistQuestionWithSurvey(survey)
        val answer = persistTextAnswer(question)

        val signUp = eventSignUpFactory.createBasic()
        signUp.event = event
        signUp.user = user
        val answers = signUp.answers as MutableSet<Answer>
        answers.clear()
        answers.add(answer)

        val saved = persist(signUp)
        assertPersisted(EventSignUp::class.java, saved.id)
    }
}
