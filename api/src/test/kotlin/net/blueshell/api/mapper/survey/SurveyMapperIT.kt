package net.blueshell.api.mapper.survey

import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.survey.Survey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SurveyMapperIT @Autowired constructor(
    private val surveyMapper: SurveyMapper,
    private val surveyDTOFactory: SurveyDTOFactory
) : MapperTestSupport() {
    @Test
    fun `persists mapped survey questions`() {
        val dto = surveyDTOFactory.createWithQuestionTypes(QuestionType.OPEN, QuestionType.RADIO)
        val survey = surveyMapper.fromDTO(dto)
        survey.questions.forEach { it.survey = survey }

        val saved = persist(survey)
        flushAndClear()

        val reloaded = reload(Survey::class.java, saved.id!!)
        val mappedDto = surveyMapper.toDTO(reloaded)

        assertThat(reloaded.questions).hasSize(dto.questions.size)
        assertThat(mappedDto.questions).hasSize(dto.questions.size)
    }
}
