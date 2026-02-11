package net.blueshell.api.factory.dto.survey

import net.blueshell.api.factory.dto.BaseDtoFactory
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.domain.survey.web.dto.QuestionDTO
import org.springframework.stereotype.Component

/**
 * Factory for QuestionDTO test instances.
 */
@Component
class QuestionDTOFactory : BaseDtoFactory<QuestionDTO>() {

    override fun targetType(): Class<QuestionDTO> = QuestionDTO::class.java

    override fun createBasic(): QuestionDTO = createByType(QuestionType.OPEN)

    fun createByType(type: QuestionType): QuestionDTO {
        val dto = QuestionDTO()
        dto.idx = nextId()
        dto.surveyId = nextId()
        dto.type = type

        when (type) {
            QuestionType.OPEN -> {
                dto.label = "What are your thoughts?"
                dto.choiceLabels = null
            }

            QuestionType.RADIO -> {
                dto.label = "Please select one option:"
                dto.choiceLabels = mutableListOf("Option A", "Option B", "Option C", "Option D")
            }

            QuestionType.CHECKBOX -> {
                dto.label = "Select all that apply:"
                dto.choiceLabels = mutableListOf("Choice 1", "Choice 2", "Choice 3", "Choice 4", "Choice 5")
            }

            QuestionType.DESCRIPTION -> {
                dto.label = "Important information:"
                dto.choiceLabels = null
            }
        }
        return dto
    }

    fun createOpen(): QuestionDTO = createByType(QuestionType.OPEN)
    fun createRadio(): QuestionDTO = createByType(QuestionType.RADIO)
    fun createCheckbox(): QuestionDTO = createByType(QuestionType.CHECKBOX)
    fun createDescription(): QuestionDTO = createByType(QuestionType.DESCRIPTION)
}
