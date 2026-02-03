package net.blueshell.api.validation.survey

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.dto.survey.AnswerDTO
import net.blueshell.api.model.survey.Question
import net.blueshell.api.repository.survey.QuestionRepository
import org.springframework.beans.factory.annotation.Autowired

@JvmRecord
data class ValidAnswerValidator @Autowired constructor(val questions: QuestionRepository?) :
    ConstraintValidator<ValidAnswer, AnswerDTO> {
    override fun isValid(dto: AnswerDTO, context: ConstraintValidatorContext?): Boolean {
        if (dto == null || dto.getQuestionId() == null) {
            return true // Let @NotNull handle this
        }

        val question = questions!!.findById(dto.getQuestionId()).orElse(null)
        if (question == null) {
            return false
        }

        return when (question.getType()) {
            QuestionType.OPEN -> dto.getTextResponse() != null && !dto.getTextResponse().trim { it <= ' ' }.isEmpty()
            QuestionType.CHECKBOX -> isValidCheckboxAnswer(dto, question)
            QuestionType.RADIO -> isValidRadioAnswer(dto, question)
            QuestionType.DESCRIPTION -> true
            else -> false
        }
    }

    private fun isValidCheckboxAnswer(dto: AnswerDTO, question: Question): Boolean {
        val selections = dto.getOptionSelections()
        val choiceLabels = question.getChoiceLabels()

        if (selections == null || choiceLabels == null) {
            return false
        }

        return selections.size == choiceLabels.size
    }

    private fun isValidRadioAnswer(dto: AnswerDTO, question: Question): Boolean {
        val selections = dto.getOptionSelections()
        val choiceLabels = question.getChoiceLabels()

        if (selections == null || choiceLabels == null || selections.size != choiceLabels.size) {
            return false
        }

        val trueCount = selections.stream().filter { obj: Boolean? -> obj.booleanValue() }.count()
        return trueCount == 1L
    }
}