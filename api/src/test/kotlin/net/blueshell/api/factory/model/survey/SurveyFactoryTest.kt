package net.blueshell.api.factory.model.survey

import net.blueshell.api.factory.model.ModelFactoryTestSupport
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
