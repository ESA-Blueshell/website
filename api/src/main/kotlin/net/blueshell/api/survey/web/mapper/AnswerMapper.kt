package net.blueshell.api.survey.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.persistence.Answer
import org.springframework.stereotype.Component

@Konverter
interface AnswerKonverter {
    fun toDTO(answer: Answer): AnswerDTO

    fun fromDTO(dto: AnswerDTO): Answer
}

@Component
class AnswerMapper : BaseMapper<Answer, AnswerDTO>() {
    private val konverter = konverter<AnswerKonverter>()

    override fun fromDTO(dto: AnswerDTO): Answer = konverter.fromDTO(dto)

    fun fromDTO(dto: AnswerDTO, answer: Answer): Answer {
        val mapped = konverter.fromDTO(dto)
        answer.questionId = mapped.questionId
        answer.optionSelections = mapped.optionSelections?.toMutableList()
        answer.textResponse = mapped.textResponse
        answer.version = dto.version
        return answer
    }

    override fun toDTO(entity: Answer): AnswerDTO = konverter.toDTO(entity)
}

fun Answer.asDTO(mapper: AnswerMapper): AnswerDTO = mapper.toDTO(this)

fun AnswerDTO.asEntity(mapper: AnswerMapper): Answer = mapper.fromDTO(this)
