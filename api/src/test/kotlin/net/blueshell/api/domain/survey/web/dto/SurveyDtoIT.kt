package net.blueshell.api.domain.survey.web.dto

import net.blueshell.api.domain.survey.application.SurveyService
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.domain.survey.web.mapping.asEntity
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SurveyDtoIT @Autowired constructor(
    private val surveyDTOFactory: SurveyDTOFactory,
    private val surveyService: SurveyService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists mapped survey questions`() {
            val dto = surveyDTOFactory.createWithQuestionTypes(QuestionType.OPEN, QuestionType.RADIO)
            val survey = dto.asEntity()
            val saved = surveyService.create(survey)
            entityManager.flush()

            val reloaded = reload(Survey::class.java, saved.id!!)

            assertThat(reloaded.questions).hasSize(dto.questions!!.size)
        }
    }
}
