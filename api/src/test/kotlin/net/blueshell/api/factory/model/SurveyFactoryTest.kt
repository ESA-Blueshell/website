package net.blueshell.api.factory.model

import net.blueshell.api.model.survey.Survey
import org.junit.jupiter.api.Test

class SurveyFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable survey`() {
        val survey = surveyFactory.createBasic()
        val saved = persist(survey)
        assertPersisted(Survey::class.java, saved.id)
    }
}
