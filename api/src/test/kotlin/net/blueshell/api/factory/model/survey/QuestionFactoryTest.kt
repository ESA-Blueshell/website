package net.blueshell.api.factory.model.survey

import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.factory.model.ModelFactoryTestSupport
import org.junit.jupiter.api.Test

class QuestionFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable question for survey`() {
        val survey = persistSurvey()
        val question = questionFactory.createForSurvey(survey)
        question.survey = persist(survey)

        val saved = persist(question)
        assertPersisted(Question::class.java, saved.id)
    }

    @Test
    fun `creates persistable multiple choice question`() {
        val survey = persistSurvey()
        val question = questionFactory.createMultipleChoice()
        question.survey = survey

        val saved = persist(question)
        assertPersisted(Question::class.java, saved.id)
    }

    @Test
    fun `creates persistable text question`() {
        val survey = persistSurvey()
        val question = questionFactory.createText()
        question.survey = survey

        val saved = persist(question)
        assertPersisted(Question::class.java, saved.id)
    }
}
