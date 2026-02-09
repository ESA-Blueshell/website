package net.blueshell.api.survey.web.mapper

import net.blueshell.api.survey.web.dto.QuestionDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.persistence.Question
import org.springframework.stereotype.Component

@Component
class QuestionMapper : BaseMapper<Question, QuestionDTO>() {
    override fun fromDTO(dto: QuestionDTO): Question = fromDTO(dto, Question())

    fun fromDTO(dto: QuestionDTO, question: Question): Question {
        dto.label?.let { question.label = it }
        dto.type?.let { question.type = it }
        dto.idx?.let { question.idx = it }
        dto.surveyId?.let { question.surveyId = it }
        dto.choiceLabels?.let { question.choiceLabels = it.filterNotNull().toMutableList() }
        dto.version?.let { question.version = it }
        return question
    }

    override fun toDTO(question: Question): QuestionDTO {
        return QuestionDTO(
            label = question.label,
            type = question.type,
            idx = question.idx,
            surveyId = question.surveyId,
            choiceLabels = question.choiceLabels?.toMutableList()
        ).also { dto ->
            dto.id = question.id
            dto.version = question.version
        }
    }
}

fun Question.asDTO(mapper: QuestionMapper): QuestionDTO = mapper.toDTO(this)

fun QuestionDTO.asEntity(mapper: QuestionMapper): Question = mapper.fromDTO(this)
