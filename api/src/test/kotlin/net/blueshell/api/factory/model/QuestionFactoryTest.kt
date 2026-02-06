package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class QuestionFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable question for survey`() {
        val survey = persistSurvey()
        val question = questionFactory.createForSurvey(survey)
        question.survey = persist(survey)

        val saved = persist(question)
        assertPersisted(net.blueshell.api.model.survey.Question::class.java, saved.id)
    }
}
