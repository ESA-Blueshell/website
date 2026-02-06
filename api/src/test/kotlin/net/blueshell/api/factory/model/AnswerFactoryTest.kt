package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class AnswerFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable answer`() {
        val survey = persistSurvey()
        val question = persistQuestionWithSurvey(survey)
        val answer = persistTextAnswer(question)
        assertPersisted(net.blueshell.api.model.survey.Answer::class.java, answer.id)
    }
}
