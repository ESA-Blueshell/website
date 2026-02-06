package net.blueshell.api.factory.model.survey

import net.blueshell.api.factory.model.ModelFactoryTestSupport
import net.blueshell.api.model.survey.Question
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
}
