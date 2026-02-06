package net.blueshell.api.mapper.survey

import net.blueshell.api.factory.dto.survey.QuestionDTOFactory
import net.blueshell.api.factory.model.QuestionFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.survey.Question
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class QuestionMapperIT @Autowired constructor(
    private val questionMapper: QuestionMapper,
    private val questionDTOFactory: QuestionDTOFactory,
) : MapperTestSupport() {
    @Test
    fun `persists survey relation`() {
        val survey = persistSurvey()
        val dto = questionDTOFactory.createOpen().apply {
            surveyId = survey.id
        }
        val question = questionFactory.createForSurvey(survey)

        val mapped = questionMapper.fromDTO(dto, question)
        val saved = persist(mapped)
        flushAndClear()

        val reloaded = reload(Question::class.java, saved.id!!)
        val mappedDto = questionMapper.toDTO(reloaded)

        assertThat(reloaded.surveyId).isEqualTo(survey.id)
        assertThat(reloaded.label).isEqualTo(dto.label)
        assertThat(mappedDto.id).isEqualTo(saved.id)
    }
}
