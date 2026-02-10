package net.blueshell.api.survey.web.dto

import net.blueshell.api.factory.dto.survey.QuestionDTOFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.survey.application.QuestionService
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.web.mapping.asEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class QuestionDtoIT @Autowired constructor(
    private val questionDTOFactory: QuestionDTOFactory,
    private val questionService: QuestionService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists survey relation`() {
            val survey = persistSurvey()
            val dto = questionDTOFactory.createOpen().apply {
                surveyId = survey.id
            }

            val mapped = dto.asEntity()
            val saved = questionService.create(mapped)
            flushAndClear()

            val reloaded = reload(Question::class.java, saved.id!!)

            assertThat(reloaded.surveyId).isEqualTo(survey.id)
            assertThat(reloaded.label).isEqualTo(dto.label)
        }
    }
}
