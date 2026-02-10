package net.blueshell.api.factory.model.event

import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.survey.persistence.Answer
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

    @Test
    fun `creates persistable event sign up for event and user`() {
        val event = persistEvent()
        val user = persistUser()
        val signUp = eventSignUpFactory.createForEventAndUser(event, user)

        val saved = persist(signUp)
        assertPersisted(EventSignUp::class.java, saved.id)
    }
}
