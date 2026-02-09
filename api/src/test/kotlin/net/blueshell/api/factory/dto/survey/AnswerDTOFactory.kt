package net.blueshell.api.factory.dto.survey

import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.survey.api.dto.AnswerDTO
import net.blueshell.api.survey.api.dto.QuestionDTO
import net.blueshell.api.survey.api.dto.SurveyDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import org.springframework.stereotype.Component

/**
 * Factory for AnswerDTO test instances.
 */
@Component
class AnswerDTOFactory : BaseDtoFactory<AnswerDTO>() {

    override fun targetType(): Class<AnswerDTO> = AnswerDTO::class.java

    override fun createBasic(): AnswerDTO {
        val dto = AnswerDTO()
        dto.questionId = nextId()
        dto.textResponse = "Sample text response"
        dto.optionSelections = null
        return dto
    }

    fun createForQuestion(question: QuestionDTO): AnswerDTO {
        val dto = AnswerDTO()
        dto.questionId = question.id ?: nextId()

        when (question.type) {
            QuestionType.OPEN -> {
                dto.textResponse = "This is a text response for the open question"
                dto.optionSelections = null
            }
            QuestionType.RADIO -> {
                dto.textResponse = null
                val choices = question.choiceLabels
                if (!choices.isNullOrEmpty()) {
                    val selections = MutableList(choices.size) { index -> index == 0 }
                    dto.optionSelections = selections
                }
            }
            QuestionType.CHECKBOX -> {
                dto.textResponse = null
                val choices = question.choiceLabels
                if (!choices.isNullOrEmpty()) {
                    val selections = MutableList(choices.size) { random.nextBoolean() }
                    dto.optionSelections = selections
                }
            }
            QuestionType.DESCRIPTION -> {
                dto.textResponse = null
                dto.optionSelections = null
            }
            else -> {
                dto.textResponse = "Default response"
                dto.optionSelections = null
            }
        }
        return dto
    }

    fun createForOpenQuestion(): AnswerDTO {
        val dto = AnswerDTO()
        dto.questionId = nextId()
        dto.textResponse = "Detailed response to the open-ended question"
        dto.optionSelections = null
        return dto
    }

    fun createForRadioQuestion(optionCount: Int, selectedIndex: Int): AnswerDTO {
        val dto = AnswerDTO()
        dto.questionId = nextId()
        dto.textResponse = null

        val selections = MutableList(optionCount) { index -> index == selectedIndex }
        dto.optionSelections = selections
        return dto
    }

    fun createForCheckboxQuestion(optionCount: Int, selectedIndices: List<Int>): AnswerDTO {
        val dto = AnswerDTO()
        dto.questionId = nextId()
        dto.textResponse = null

        val selections = MutableList(optionCount) { index -> selectedIndices.contains(index) }
        dto.optionSelections = selections
        return dto
    }

    fun createForSurvey(survey: SurveyDTO): MutableList<AnswerDTO> {
        return survey.questions.map { question -> createForQuestion(question) }.toMutableList()
    }
}
