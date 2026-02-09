package net.blueshell.api.survey.web.mapper

import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.persistence.Answer
import org.springframework.stereotype.Component

@Component
class AnswerMapper : BaseMapper<Answer, AnswerDTO>() {
    override fun fromDTO(dto: AnswerDTO): Answer = fromDTO(dto, Answer())

    fun fromDTO(dto: AnswerDTO, answer: Answer): Answer {
        dto.questionId?.let { answer.questionId = it }
        answer.optionSelections = dto.optionSelections?.toMutableList()
        answer.textResponse = dto.textResponse
        dto.version?.let { answer.version = it }
        return answer
    }

    override fun toDTO(answer: Answer): AnswerDTO {
        return AnswerDTO(
            questionId = answer.questionId,
            optionSelections = answer.optionSelections?.toMutableList(),
            textResponse = answer.textResponse
        ).also { dto ->
            dto.id = answer.id
            dto.version = answer.version
        }
    }
}

fun Answer.asDTO(mapper: AnswerMapper): AnswerDTO = mapper.toDTO(this)

fun AnswerDTO.asEntity(mapper: AnswerMapper): Answer = mapper.fromDTO(this)
