package net.blueshell.api.factory.model.survey

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.survey.domain.model.Answer
import org.junit.jupiter.api.Test

class AnswerFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable answer`() {
        val survey = persistSurvey()
        val question = persistQuestionWithSurvey(survey)
        val answer = persistTextAnswer(question)
        assertPersisted(Answer::class.java, answer.id)
    }

    @Test
    fun `creates persistable answer for question`() {
        val survey = persistSurvey()
        val question = persistQuestionWithSurvey(survey)
        val answer = answerFactory.createForQuestion(question)

        val saved = persist(answer)
        assertPersisted(Answer::class.java, saved.id)
    }
}
