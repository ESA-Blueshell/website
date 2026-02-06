package net.blueshell.api.factory.model

import net.blueshell.api.model.survey.Answer
import org.junit.jupiter.api.Test

class AnswerFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable answer`() {
        val survey = persistSurvey()
        val question = persistQuestionWithSurvey(survey)
        val answer = persistTextAnswer(question)
        assertPersisted(Answer::class.java, answer.id)
    }
}
