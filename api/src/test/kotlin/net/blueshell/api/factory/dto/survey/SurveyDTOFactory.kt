package net.blueshell.api.factory.dto.survey

import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.dto.survey.QuestionDTO
import net.blueshell.api.dto.survey.SurveyDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for SurveyDTO test instances.
 */
@Component
class SurveyDTOFactory(
    private val questionFactory: QuestionDTOFactory
) : BaseDtoFactory<SurveyDTO>() {

    override fun targetType(): Class<SurveyDTO> = SurveyDTO::class.java

    override fun createBasic(): SurveyDTO = createWithQuestionTypes(QuestionType.OPEN, QuestionType.RADIO)

    fun createWithQuestionTypes(vararg questionTypes: QuestionType): SurveyDTO {
        val dto = SurveyDTO()

        val questions = questionTypes.map { questionFactory.createByType(it) }.toMutableList()
        for (i in questions.indices) {
            questions[i].idx = (i + 1).toLong()
        }

        dto.questions = questions
        dto.responseCount = 0L
        return dto
    }

    fun createWithOpenQuestions(count: Int): SurveyDTO {
        val types = Array(count) { QuestionType.OPEN }
        return createWithQuestionTypes(*types)
    }

    fun createWithMixedQuestions(): SurveyDTO {
        return createWithQuestionTypes(
            QuestionType.DESCRIPTION, QuestionType.RADIO, QuestionType.CHECKBOX, QuestionType.OPEN
        )
    }

    fun createWithMultipleChoiceOnly(): SurveyDTO {
        return createWithQuestionTypes(
            QuestionType.RADIO, QuestionType.RADIO, QuestionType.CHECKBOX
        )
    }
}
